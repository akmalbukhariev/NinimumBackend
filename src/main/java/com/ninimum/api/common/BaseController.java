package com.ninimum.api.common;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BaseController {
    private String apiVersion;

    protected void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    protected VersionResponseResult setResult(Result result) {
        return setResult(result, null);

    }

    protected VersionResponseResult setResult(Result result, Object obj) {
        VersionResponseResult resResult = new VersionResponseResult();
        resResult.setResultCode(Integer.toString(result.getCode()));
        resResult.setResultMsg(result.getMessage());
        resResult.setApiVersion(apiVersion);
        resResult.setResultData(obj);

        return resResult;
    }

    protected VersionResponseResult setResultForBlockUser(Result result, LocalDateTime untillDate) {
        VersionResponseResult resResult = new VersionResponseResult();
        resResult.setResultCode(Integer.toString(result.getCode()));

        if (untillDate != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            resResult.setResultMsg(untillDate.format(formatter));
        } else {
            resResult.setResultMsg(null);
        }
        resResult.setApiVersion(apiVersion);
        resResult.setResultData(null);

        return resResult;
    }

    protected String uploadProfilePicture(MultipartFile file, String anyName, String relativeDir) throws IOException {
        // Detect OS
        String os = System.getProperty("os.name").toLowerCase();
        String uploadDir;

        if (os.contains("mac")) {
            // Save inside the project folder on MacBook
            String projectDir = new File("").getAbsolutePath();
            uploadDir = projectDir + File.separator + relativeDir;
        } else {
            // Use the given relative directory (which is absolute on Windows)
            uploadDir = relativeDir;
        }

        // Create the directory if it doesn't exist
        File directory = new File(uploadDir);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // Generate a unique file name using the phone number and the original file extension
        String fileExtension = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.'));
        String fileName = anyName + "_" + System.currentTimeMillis() + fileExtension;

        // Create the file on the disk
        File destinationFile = new File(directory, fileName);
        file.transferTo(destinationFile);

        // Return the full file path
        return fileName;
    }

    protected String deleteFile(String strFile){
        String result = "";
        File file = new File(strFile);
        if (file.exists()) {
            if (file.delete()) {
                result = "Successfully deleted old profile picture: " + strFile;
            } else {
                result = "Failed to delete old profile picture: " + strFile;
            }
        } else {
            result = "Old profile picture does not exist: {}" + strFile;
        }

        return result;
    }
}
