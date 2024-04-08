package com.shuking.ojcodesandbox.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.shuking.ojcodesandbox.model.ExecuteCodeRequest;
import com.shuking.ojcodesandbox.model.ExecuteCodeResponse;
import com.shuking.ojcodesandbox.model.ExecuteMessage;
import com.shuking.ojcodesandbox.model.JudgeInfo;
import com.shuking.ojcodesandbox.util.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 原生Java 代码沙箱模板方法的实现
 */
@Slf4j
@SuppressWarnings("all")
public abstract class JavaCodeSandBoxTemplate implements CodeSandBox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;

    // 完整流程调用
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputs();
        String code = executeCodeRequest.getCode();

        //        1. 把用户的代码保存为文件
        File userCodeFile = saveCodeToFile(code);

        //        2. 编译代码，得到 class 文件
        ExecuteMessage compileFileExecuteMessage = compileFile(userCodeFile);

        //          3. 执行代码，得到输出结果
        List<ExecuteMessage> executeMessageList = runFile(userCodeFile, inputList);

        //        4. 收集整理输出结果
        ExecuteCodeResponse outputResponse = getOutputResponse(executeMessageList);

        //        5. 文件清理
        boolean b = deleteFile(userCodeFile);
        if (!b) {
            log.error("deleteFile error, userCodeFilePath = {}", userCodeFile.getAbsolutePath());
        }
        System.out.println(outputResponse);
        return outputResponse;
    }


    /**
     * 1. 把用户的代码保存为文件
     *
     * @param code 用户代码
     * @return 保存的文件
     */
    public File saveCodeToFile(String code) {
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        // 判断全局代码目录是否存在，没有则新建
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }

        // 把用户的代码隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    /**
     * 2、编译代码
     *
     * @param userCodeFile 保存的用户代码文件
     * @return 执行信息
     */
    public ExecuteMessage compileFile(File userCodeFile) {
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcess(compileProcess, "compile");
            if (executeMessage.getExitValue() != 0) {
                throw new RuntimeException("compile error");
            }
            return executeMessage;
        } catch (Exception e) {
            throw new RuntimeException("compile error", e);
        }
    }

    /**
     * 3、执行文件，获得执行结果列表
     *
     * @param userClassFile 编译后的文件
     * @param inputList     输入列表
     * @return 执行信息列表
     */
    public List<ExecuteMessage> runFile(File userClassFile, List<String> inputList) {
        String userCodeParentPath = userClassFile.getParentFile().getAbsolutePath();

        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                // 超时控制
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException("execute error",e);
                    }
                }).start();
                // ExecuteMessage executeMessage = ProcessUtils.runProcess(runProcess, "运行");
                ExecuteMessage executeMessage = ProcessUtils.runInterActiveProcess(runProcess, inputArgs, "execute");

                if (executeMessage.getExitValue() != 0) {
                    throw new RuntimeException("execute error");
                }
                executeMessageList.add(executeMessage);
            } catch (Exception e) {
                throw new RuntimeException("execute error", e);
            }
        }
        return executeMessageList;
    }

    /**
     * 4、获取输出结果
     *
     * @param executeMessageList 收集的执行信息
     * @return 返回给判题服务的结果
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        // 取用时最大值，便于判断是否超时
        long maxTime = 0, maxMemory = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                // 用户提交的代码执行中存在错误
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            Long memory = executeMessage.getMemory();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
            if (memory != null) {
                maxMemory = Math.max(maxMemory, memory);
            }
        }
        // 正常运行完成
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        executeCodeResponse.setJudgeInfo(new JudgeInfo("", maxMemory, maxTime));
        return executeCodeResponse;
    }

    /**
     * 5、删除文件
     *
     * @param userCodeFile 用户代码文件
     * @return 是否成功
     */
    public boolean deleteFile(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

    /**
     * 6、获取错误响应
     *
     * @return
     */
    public ExecuteCodeResponse getExeResponse(String errMsg) {
        return ExecuteCodeResponse.builder().outputList(new ArrayList<>())
                // 2--代码沙箱自身错误
                .judgeInfo(new JudgeInfo(errMsg, null, null))
                .message(errMsg).status(2).build();
    }
}