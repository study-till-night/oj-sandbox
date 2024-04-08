package com.shuking.ojcodesandbox.exception;

import com.shuking.ojcodesandbox.model.ExecuteCodeResponse;
import com.shuking.ojcodesandbox.model.JudgeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

/**
 * 全局异常处理器
 *
 * @author shu
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    /**
     * 线程处理超时
     *
     * @param e
     * @return
     */
    @ExceptionHandler(TimeoutException.class)
    public void threadTimeOutExceptionHandler(TimeoutException e) {
        log.error("TimeoutException {}", e.getMessage());
    }

    /**
     * 编译及执行异常处理
     *
     * @param e
     * @return
     */
    @ExceptionHandler(RuntimeException.class)
    public ExecuteCodeResponse runtimeExceptionHandler(RuntimeException e) {
        String errMsg = e.getMessage();
        log.error("RuntimeException {}", errMsg);

        return ExecuteCodeResponse.builder()
                .judgeInfo(new JudgeInfo(errMsg, null, null))
                .message(errMsg)
                .status(3)
                .outputList(new ArrayList<>())
                .build();
    }
}