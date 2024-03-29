package com.trust.xfyl.controller;


import com.trust.xfyl.entity.dto.CompletionRequestDTO;
import com.trust.xfyl.entity.dto.CompletionResponseDTO;
import com.trust.xfyl.entity.dto.Result;
import com.trust.xfyl.enums.ErrorCodeEnum;
import com.trust.xfyl.exception.BizException;
import com.trust.xfyl.handler.ChatServiceHandler;
import com.trust.xfyl.service.ChatSessionService;
import com.trust.xfyl.util.BailianLlmClient;
import com.trust.xfyl.util.LogUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

/**
 * chat conversation controller
 *
 * @author yuanci
 */

@RestController
@RequestMapping("/v1")
public class CompletionController {
    @Resource
    private BailianLlmClient llmClient;

    @Resource
    private ChatSessionService chatSessionService;

    @RequestMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Result<CompletionResponseDTO>> complete(@RequestBody CompletionRequestDTO completionRequest,
                                                        HttpServletResponse response) {
        long startTime = System.currentTimeMillis();
        String requestId = completionRequest.getRequestId();
        LogUtils.trace(requestId, "completion", "SUCCESS", startTime, completionRequest, null);

        try {
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("X-Accel-Buffering", "no");

            String content = completionRequest.getContent();
            if (StringUtils.isBlank(requestId) || StringUtils.isBlank(content)) {
                LogUtils.monitor(requestId, "CompletionController", "complete", "error",
                        startTime, completionRequest, ErrorCodeEnum.PARAMS_INVALID.getErrorMessage());
                Result<CompletionResponseDTO> error = Result.error(requestId, ErrorCodeEnum.PARAMS_INVALID);

                return Flux.just(error);
            }

            ChatServiceHandler handler = new ChatServiceHandler(chatSessionService, llmClient);
            Flux<Result<CompletionResponseDTO>> handle = handler.handle(completionRequest);
            return handle;
        } catch (BizException e) {
            LogUtils.monitor(requestId, "CompletionController", "complete", "error",
                    startTime, completionRequest, e);
            Result<CompletionResponseDTO> error = Result.error(requestId, e.getErrorCode(), e.getMessage());

            return Flux.just(error);
        } catch (Exception e) {
            LogUtils.monitor(requestId, "CompletionController", "complete", "error",
                    startTime, completionRequest, e);
            Result<CompletionResponseDTO> error = Result.error(requestId, ErrorCodeEnum.CREATE_COMPLETION_ERROR);

            return Flux.just(error);
        }
    }

    @RequestMapping(value = "/stopGeneration", produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<String> stopGeneration(@RequestBody CompletionRequestDTO request) {
        Long start = System.currentTimeMillis();
        System.out.println(start+"================================");
        //TODO actually, it's do nothing now, maybe need to do some records,
        LogUtils.monitor(request.getRequestId(), "CompletionController", "stopGeneration", null, start, request, null);
        return Result.success(request.getRequestId(), request.getRequestId());
    }
}
