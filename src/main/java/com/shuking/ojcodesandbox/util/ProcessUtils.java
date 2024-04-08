package com.shuking.ojcodesandbox.util;

import com.shuking.ojcodesandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StopWatch;

import java.io.*;
import java.util.ArrayList;

@Slf4j
public class ProcessUtils {
    /**
     * 执行进程
     *
     * @param process 进程
     * @param msg     执行的操作
     * @return 执行信息
     */
    public static ExecuteMessage runProcess(Process process, String msg) {
        ExecuteMessage executeMessage = ExecuteMessage.builder().build();

        try {
            // 等待进程结束 得到错误码
            int exitValue = process.waitFor();
            executeMessage.setExitValue(exitValue);

            if (exitValue == 0) {
                log.info("{} 成功", msg);
                // 分批获取进程正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                // 按行得到编译输出信息
                String outPutLine;
                ArrayList<String> outputStrList = new ArrayList<>();
                while ((outPutLine = bufferedReader.readLine()) != null) {
                    outputStrList.add(outPutLine);
                }
                executeMessage.setMessage(StringUtils.join(outputStrList,"\n"));
            } else {
                log.info("{} 失败", msg);
                // 分批获取进程正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                // 按行得到编译输出信息
                String outPutLine;
                StringBuilder outPutBuilder = new StringBuilder();
                while ((outPutLine = bufferedReader.readLine()) != null) {
                    outPutBuilder.append(outPutLine);
                }
                executeMessage.setMessage(outPutBuilder.toString());
                // 分批获取进程错误输出
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                // 按行得到编译输出信息
                String errorOutPutLine;
                ArrayList<String> outputStrList = new ArrayList<>();
                while ((errorOutPutLine = errorBufferedReader.readLine()) != null) {
                    outputStrList.add(errorOutPutLine);
                }
                executeMessage.setMessage(StringUtils.join(outputStrList,"\n"));
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(String.format("%s error",msg),e);
        }
        return executeMessage;
    }

    /**
     * 执行交互式进程
     *
     * @param process 进程
     * @param msg     执行的操作
     * @param args    输入的参数
     * @return 执行信息
     */
    public static ExecuteMessage runInterActiveProcess(Process process, String args, String msg) {
        ExecuteMessage executeMessage = ExecuteMessage.builder().build();

        try {
            OutputStream outputStream = process.getOutputStream();
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            // 用换行符分割每个输入变量
            /*String inputs = String.join("\n",
                    Optional.ofNullable(StringUtils.split(args, " ")).orElse(new String[]{})) + "\n";*/
            // 向控制台输入数据
            outputStreamWriter.write(args+"\n");
            outputStreamWriter.flush();

            StopWatch timeWatcher = new StopWatch();
            timeWatcher.start();
            // 等待进程结束 得到错误码
            int exitValue = process.waitFor();
            timeWatcher.stop();
            // 获取程序执行耗时
            long timeUsed = timeWatcher.getTotalTimeMillis();
            executeMessage.setTime(timeUsed);
            executeMessage.setExitValue(exitValue);

            if (exitValue == 0) {
                log.info("{} 成功", msg);
                // 分批获取进程正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                // 按行得到编译输出信息
                String outPutLine;
                ArrayList<String> outputStrList = new ArrayList<>();
                while ((outPutLine = bufferedReader.readLine()) != null) {
                    outputStrList.add(outPutLine);
                }
                executeMessage.setMessage(StringUtils.join(outputStrList,"\n"));
                bufferedReader.close();
            } else {
                log.info("{} 失败", msg);
                // 分批获取进程正常输出
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                // 按行得到编译输出信息
                String outPutLine;
                ArrayList<String> outputStrList = new ArrayList<>();
                while ((outPutLine = bufferedReader.readLine()) != null) {
                    outputStrList.add(outPutLine);
                }
                executeMessage.setMessage(StringUtils.join(outputStrList,"\n"));
                // 分批获取进程错误输出
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                // 按行得到编译输出信息
                String errorOutPutLine;
                StringBuilder errorOutPutBuilder = new StringBuilder();
                while ((errorOutPutLine = errorBufferedReader.readLine()) != null) {
                    errorOutPutBuilder.append(errorOutPutLine);
                }
                executeMessage.setErrorMessage(errorOutPutBuilder.toString());
                bufferedReader.close();
            }
            // 释放资源
            outputStream.close();
            outputStreamWriter.close();
            process.destroy();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(String.format("%s error",msg),e);
        }
        System.out.println(executeMessage);
        return executeMessage;
    }
}