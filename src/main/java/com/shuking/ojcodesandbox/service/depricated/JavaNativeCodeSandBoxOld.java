package com.shuking.ojcodesandbox.service.depricated;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import com.shuking.ojcodesandbox.model.ExecuteCodeRequest;
import com.shuking.ojcodesandbox.model.ExecuteCodeResponse;
import com.shuking.ojcodesandbox.model.ExecuteMessage;
import com.shuking.ojcodesandbox.model.JudgeInfo;
import com.shuking.ojcodesandbox.service.CodeSandBox;
import com.shuking.ojcodesandbox.util.ProcessUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * java原生代码沙箱实现
 */
// @SuppressWarnings("all")
public class JavaNativeCodeSandBoxOld implements CodeSandBox {

    // 敏感词列表
    private static final List<String> BLACK_WORD_LIST = Arrays.asList("File", "exec", "Runtime", "write", "read");
    // private static List<String> BLACK_WORD_LIST;

    // 字典树
    private static final WordTree WORDTREE;
    // 临时文件夹名称
    private static final String TEMP_DIR_PATH = "tmpCode";
    // 主类名称要求必须与文件名一致才能编译通过
    private static final String DEFAULT_CLASS_NAME = "testCode/Main.java";

    // 字典树初始化
    static {
        WORDTREE = new WordTree();
        WORDTREE.addWords(BLACK_WORD_LIST);
    }

    public static void main(String[] args) {
        JavaNativeCodeSandBoxOld javaBox = new JavaNativeCodeSandBoxOld();

        ExecuteCodeRequest request = new ExecuteCodeRequest(Arrays.asList("1 2", "100 200"), "java",
                ResourceUtil.readStr("E:\\java\\javaidea\\oj-codesandbox\\src\\main\\resources\\error\\WriteFile.java", StandardCharsets.UTF_8));
        ExecuteCodeResponse executeCode = javaBox.executeCode(request);
    }

    /**
     * 执行代码
     *
     * @param request 请求体
     * @return 响应体
     */
    @Override
    // todo    对子程序(用户提交的代码进行权限校验) SecurityManager已弃用！！！
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
        long start = System.currentTimeMillis();

        List<String> inputs = request.getInputs();
        String code = request.getCode();

        // 代码合法性校验
        if (StringUtils.isNotBlank(WORDTREE.match(code))) {
            System.out.println("代码存在危险！");
            return ExecuteCodeResponse.builder()
                    .message("代码存在危险").outputList(new ArrayList<>()).status(3).judgeInfo(null).build();
        }
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

        // 3--模拟命令行执行代码
        ArrayList<ExecuteMessage> exeMsgList = new ArrayList<>();
        try {
            // 遍历每个测试用例
            for (String input : inputs) {
                // 得到执行进程   --使用cmd传参形式
                /*Process compileProcess = Runtime.getRuntime().exec(String.format("java -cp %s Main %s", userCodePath, input));
                ExecuteMessage executeMsg = ProcessUtil.runProcess(compileProcess, "代码执行");*/

                // 得到执行进程   --使用程序输入形式
                // todo -Xmx 程序最大堆内存空间 -Xms 程序初始堆内存空间
                Process exeProcess = Runtime.getRuntime().exec(String.format("java -cp %s Main", userCodePath));
                // 超时控制
                new Thread(() -> {
                    try {
                        // 在进程运行前开启一个新线程 若线程2s后还在运行 说明主进程已超时 手动进行销毁并进行超时提示
                        Thread.sleep(2000);
                        System.out.println("程序超时");
                        exeProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMsg = ProcessUtils.runInterActiveProcess(exeProcess, input, "代码执行");

                // 若执行失败 直接返回
                if (executeMsg != null && executeMsg.getExitValue() != 0) {
                    return getExeResponse("启动代码程序失败");
                }
                exeMsgList.add(executeMsg);
            }
        } catch (IOException e) {
            return getExeResponse("启动代码程序失败");
        }

        // 4--收集整理输出
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        // 程序执行输出集合
        ArrayList<String> outputList = new ArrayList<>();
        // 测试用例中最长耗时
        long maxTimeUsed = 0;
        for (ExecuteMessage executeMessage : exeMsgList) {
            // 若错误信息不为空 则向外传递
            if (StrUtil.isNotBlank(executeMessage.getErrorMessage())) {
                executeCodeResponse.setMessage(executeMessage.getErrorMessage());
                // 3--用户代码执行过程中出错
                executeCodeResponse.setStatus(3);
            }
            maxTimeUsed = Math.max(maxTimeUsed, executeMessage.getTime());
            outputList.add(executeMessage.getMessage());
        }
        // 若每次都成功输出 则设置状态为成功
        if (outputList.size() == exeMsgList.size()) {
            executeCodeResponse.setStatus(1);
        }
        JudgeInfo judgeInfo = new JudgeInfo();
        // judgeInfo.setMemory();
        judgeInfo.setTime(maxTimeUsed);
        executeCodeResponse.setJudgeInfo(judgeInfo);
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