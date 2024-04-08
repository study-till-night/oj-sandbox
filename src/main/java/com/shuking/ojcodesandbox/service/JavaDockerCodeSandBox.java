package com.shuking.ojcodesandbox.service;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.shuking.ojcodesandbox.model.ExecuteCodeRequest;
import com.shuking.ojcodesandbox.model.ExecuteCodeResponse;
import com.shuking.ojcodesandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JavaDockerCodeSandBox extends JavaCodeSandBoxTemplate {
    // 超时时间
    private static final long TIME_OUT = 5000L;

    // 是否为第一次开启服务
    private static Boolean firstInit = true;

    public static void main(String[] args) {
        JavaDockerCodeSandBox javaDockerCodeSandBox = new JavaDockerCodeSandBox();
        ExecuteCodeRequest request = new ExecuteCodeRequest(Arrays.asList("1 2", "100 200"), "java",
                ResourceUtil.readStr("testCode/Main.java", StandardCharsets.UTF_8));
        ExecuteCodeResponse executeCode = javaDockerCodeSandBox.executeCode(request);
        System.out.println(executeCode);
    }

    @Override
    public List<ExecuteMessage> runFile(File userClassFile, List<String> inputList) {
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
                throw new RuntimeException(e.getMessage());
            }
        }

        // 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(javaImage);
        // 配置容器参数
        HostConfig hostConfig = new HostConfig();
        // 设置容器卷映射  将编程后的代码文件映射到容器内部
        hostConfig.setBinds(new Bind(userClassFile.getParentFile().getAbsolutePath(), new Volume("/app")));
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

        for (String input : inputList) {
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
                log.error("docker容器执行程序出错");
                throw new RuntimeException(e.getMessage());
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
        return exeMsgList;
    }
}