package com.shuking.ojcodesandbox.controller;

import com.shuking.ojcodesandbox.model.ExecuteCodeRequest;
import com.shuking.ojcodesandbox.model.ExecuteCodeResponse;
import com.shuking.ojcodesandbox.service.JavaNativeCodeSandBox;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/sandbox")
public class CodeSandBoxController {

    @Resource
    private JavaNativeCodeSandBox javaNativeCodeSandBox;

    //  鉴权请求头名称
    private static final String AUTH_HEADER = "sandbox-header";
    //  密钥字符串
    private static final String AUTH_KEY = "auth-key";

    /**
     * 心跳检测
     *
     * @return  ok
     */
    @GetMapping("/health")
    public String healthCheck() {
        return "ok";
    }

    /**
     * 执行代码沙箱
     * @param codeRequest   包含提交信息的对象
     * @return  执行结果
     */
    @PostMapping("/execute")
    public ExecuteCodeResponse doExecute(@RequestBody ExecuteCodeRequest codeRequest,
                                         HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        String header = httpServletRequest.getHeader(AUTH_HEADER);
        // 不合规请求
        /*if (!header.equals(AUTH_KEY)) {
            httpServletResponse.setStatus(401);
            return null;
        }*/

        if (codeRequest == null)
            throw new RuntimeException("请求参数为空");
        return javaNativeCodeSandBox.executeCode(codeRequest);
    }
}