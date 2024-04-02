package com.shuking.ojcodesandbox.service;


import com.shuking.ojcodesandbox.model.ExecuteCodeRequest;
import com.shuking.ojcodesandbox.model.ExecuteCodeResponse;

@FunctionalInterface
public interface CodeSandBox {
    // 使用接口定义执行方法提高通用性
    ExecuteCodeResponse executeCode(ExecuteCodeRequest request);
}
