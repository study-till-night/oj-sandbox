package com.shuking.ojcodesandbox.service.depricated;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.shuking.ojcodesandbox.model.ExecuteCodeRequest;
import com.shuking.ojcodesandbox.model.ExecuteCodeResponse;
import com.shuking.ojcodesandbox.model.ExecuteMessage;
import com.shuking.ojcodesandbox.model.JudgeInfo;
import com.shuking.ojcodesandbox.service.CodeSandBox;
import com.shuking.ojcodesandbox.util.ProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Docker代码沙箱实现 声明为抽象类 以便可以重写方法
 */
@SuppressWarnings("all")
@Slf4j
@Deprecated
public class JavaDockerCodeSandBoxOld implements CodeSandBox {


    // 临时文件夹名称
    private static final String TEMP_DIR_PATH = "tmpCode";
    // 主类名称要求必须与文件名一致才能编译通过
    private static final String DEFAULT_CLASS_NAME = "Main.java";
    // 超时时间
    private static final long TIME_OUT = 5000L;
    // linux seccomp安全配置json
    private static final String SECURITY_CONF = "{\n" +
            "  \"defaultAction\": \"SCMP_ACT_ERRNO\",\n" +
            "  \"syscalls\": [\n" +
            "    {\n" +
            "      \"names\": [\"read\", \"pread\", \"preadv\", \"open\"],\n" +
            "      \"action\": \"SCMP_ACT_ALLOW\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"names\": [\"write\", \"pwrite\", \"pwritev\", \"open\"],\n" +
            "      \"action\": \"SCMP_ACT_ERRNO\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    // 是否为第一次开启服务
    private static Boolean firstInit = true;

    public static void main(String[] args) {
        JavaDockerCodeSandBoxOld javaBox = new JavaDockerCodeSandBoxOld();

        ExecuteCodeRequest request = new ExecuteCodeRequest(Arrays.asList("1 2", "100 200"), "java",
                ResourceUtil.readStr("testCode/Main.java", StandardCharsets.UTF_8));
        ExecuteCodeResponse executeCode = javaBox.executeCode(request);
    }

    /**
     * 执行代码
     *
     * @param request 请求体
     * @return 响应体
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
        long start = System.currentTimeMillis();

        List<String> inputs = request.getInputs();
        String code = request.getCode();


        // 得到临时文件的路径
        String userDir = System.getProperty("user.dir");
        String globalCodePath = userDir + File.separator + TEMP_DIR_PATH;

        if (FileUtil.exist(globalCodePath)) {
            FileUtil.mkdir(globalCodePath);
        }
        // 1--将代码转化成文件
        // 每次执行都生成一个UUID的临时文件夹 以防Main主类名的冲突
        String userCodePath = globalCodePath + File.separator + UUID.randomUUID() + File.separator;
        // 得到临时代码文件
        File tempCodeFile = FileUtil.writeString(code, userCodePath + DEFAULT_CLASS_NAME, StandardCharsets.UTF_8);

        // 2--编译代码 得到字节码文件
        try {
            // 得到编译进程
            Process compileProcess = Runtime.getRuntime().exec(String.format("javac -encoding utf-8 %s", tempCodeFile.getAbsolutePath()));
            ExecuteMessage compileMsg = ProcessUtils.runProcess(compileProcess, "代码编译");
            // 若编译失败 直接返回
            if (compileMsg.getExitValue() != 0)
                return getExeResponse("程序编译失败");
        } catch (IOException e) {
            return getExeResponse(e.getMessage());
        }

        // 3--初始化docker     以java17 作为基础镜像
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        // String javaImage = "openjdk:8-alpine";
        String javaImage = "openjdk:17-jdk";

        // 首次执行拉取java镜像
        if (!firstInit) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(javaImage);
            PullImageResultCallback pullCallBack = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    log.info("下载镜像:" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                // 执行拉取操作 并等待完成
                pullImageCmd.exec(pullCallBack).awaitCompletion();
                firstInit = false;
                log.info("拉取完成");
            } catch (InterruptedException e) {
                log.error("拉取镜像异常");
                return getExeResponse(e.getMessage());
            }
        }

        // 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(javaImage);
        // 配置容器参数
        HostConfig hostConfig = new HostConfig();
        // 设置容器卷映射  将编程后的代码文件映射到容器内部
        hostConfig.setBinds(new Bind(userCodePath, new Volume("/app")));
        // 限制内存最高128MB cpu核数1
        hostConfig.withMemory(128 * 1000 * 1000L);
        hostConfig.withCpuCount(1L);
        hostConfig.withReadonlyRootfs(true);    //  限制根目录为只读
        // Seccomp（Secure Computing Mode）是一种内核安全机制，它可以限制一个进程所能执行的系统调用
        // hostConfig.withSecurityOpts(Collections.singletonList(String.format("seccomp=%s", SECURITY_CONF)));
        CreateContainerResponse response = containerCmd.withAttachStdin(true)
                .withNetworkDisabled(true)  //  禁用网络传输
                .withHostConfig(hostConfig)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true).exec();

        String containerId = response.getId();
        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();
        // 与容器进行交互  依次执行测试用例
        ArrayList<ExecuteMessage> exeMsgList = new ArrayList<>();

        for (String input : inputs) {
            // 将输入参数进行分割
            String[] args = input.split(" ");
            // 得到最终执行java程序的指令
            String[] exeCmdStr = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, args);
            // 创建交互
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withCmd(exeCmdStr)
                    .exec();
            // 返回给判题模块的对象
            // 正常/异常输出
            final String[] message = {""};
            final String[] errorMessage = {""};
            // 执行耗时
            long time;
            // 占用内存
            long[] maxMemory = {0L};
            // 执行命令的回调
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessage[0] = new String(frame.getPayload());
                        log.error("输出错误结果：" + errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload());
                        log.info("输出结果：" + message[0]);
                    }
                    super.onNext(frame);
                }
            };
            // 状态监控
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            // 获取占用的内存
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {

                @Override
                public void onNext(Statistics statistics) {
                    System.out.println("内存占用：" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(Optional.ofNullable(statistics.getMemoryStats().getUsage()).orElse(0L), maxMemory[0]);
                }

                @Override
                public void close() {
                }

                @Override
                public void onStart(Closeable closeable) {
                }

                @Override
                public void onError(Throwable throwable) {
                }

                @Override
                public void onComplete() {
                }
            });
            // 执行状态监控
            statsCmd.exec(statisticsResultCallback);
            try {
                // 监控程序执行时间
                StopWatch stopWatch = new StopWatch();
                stopWatch.start();
                // 执行命令
                dockerClient.execStartCmd(execCreateCmdResponse.getId())
                        .exec(execStartResultCallback).awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                time = stopWatch.getTotalTimeMillis();
                statsCmd.close();
            } catch (InterruptedException e) {
                return getExeResponse("执行docker程序出错");
            }
            ExecuteMessage executeMessage = ExecuteMessage.builder()
                    .message(message[0])    //  程序输出
                    .errorMessage(errorMessage[0])  //  错误输出
                    .time(time) //  耗时
                    .memory(maxMemory[0]).build();  //  占用内存
            exeMsgList.add(executeMessage);
        }
        // 停止后删除容器
        dockerClient.stopContainerCmd(containerId).exec();
        dockerClient.removeContainerCmd(containerId).exec();

        // 4--收集输出结果
        // 测试用例中最长耗时/内存占用
        long maxTimeUsed = 0, maxMemoryUsed = 0;
        // 程序执行输出集合
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        ArrayList<String> outputList = new ArrayList<>();

        for (ExecuteMessage executeMessage : exeMsgList) {
            // 若错误信息不为空 则向外传递
            if (StrUtil.isNotBlank(executeMessage.getErrorMessage())) {
                executeCodeResponse.setMessage(executeCodeResponse.getMessage() + executeMessage.getErrorMessage());
                // 3--用户代码执行过程中出错
                executeCodeResponse.setStatus(3);
            }
            maxTimeUsed = Math.max(maxTimeUsed, executeMessage.getTime());
            maxMemoryUsed = Math.max(maxMemoryUsed, executeMessage.getMemory());

            outputList.add(executeMessage.getMessage());
        }
        // 若每次都成功输出 则设置状态为成功
        if (outputList.size() == exeMsgList.size()) {
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setJudgeInfo(new JudgeInfo("", maxMemoryUsed, maxTimeUsed));
        executeCodeResponse.setOutputList(outputList);
        // 5--清理临时生成的文件
        if (FileUtil.exist(userCodePath)) {
            FileUtil.del(userCodePath);
        }

        System.out.printf("代码沙箱执行耗时---%s  ms%n", System.currentTimeMillis() - start);
        return executeCodeResponse;
    }

    /**
     * 当程序执行异常时直接抛出特定对象
     *
     * @return 异常执行响应
     */
    private ExecuteCodeResponse getExeResponse(String errMsg) {
        return ExecuteCodeResponse.builder().outputList(new ArrayList<>())
                // 2--代码沙箱自身错误
                .judgeInfo(new JudgeInfo()).message(errMsg).status(2).build();
    }
}