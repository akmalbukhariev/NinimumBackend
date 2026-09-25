package com.ninimum.api.configure;

import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
public class MultipartExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<VersionResponseResult> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return response("Uploaded image is too large. Maximum file size is 20 MB.", HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<VersionResponseResult> handleMultipart(MultipartException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = "Could not process the uploaded image.";
        }
        return response(message, HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<VersionResponseResult> response(String message, HttpStatus status) {
        VersionResponseResult result = new VersionResponseResult();
        result.setResultCode("302");
        result.setResultMsg(message);
        result.setResultData(null);
        result.setApiVersion(Constant.api_version);
        return new ResponseEntity<>(result, status);
    }
}
