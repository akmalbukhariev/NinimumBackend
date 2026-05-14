package com.ninimum.api.constants;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Constant {
    public static final String SERVER_IP = "95.182.118.233";
    public static final String SERVER_DOMAIN = "www.saletop.uz";
    public static final String SERVER_HTTP = "http://" + SERVER_DOMAIN;
    public static final String SERVER_HTTPS = "https://" + SERVER_IP;

    public static final String api_version = "1.0.0";
    public static final String basePackageUser = "com.ninimum.api";
    public static final String HEADER_AUTH = "Authorization";
    public static final String HEADER_BEARER = "Bearer";
    public static final String HEADER_ACCESS_TOKEN = "access-token";
    public static final String HEADER_REFRESH_TOKEN = "refresh-token";
    public static final String HEADER_ROLE = "auth-role";
    public static final String HEADER_USER_NAME = "user-name";
    public static final Path UPLOAD_DIRECTORY = Paths.get("images/");
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_SERVICE = "ROLE_SERVICE";
    public static final String ROLE_COMPANY = "ROLE_COMPANY";
    public static final String COMPANY = "COMPANY";
    public static final String USER = "USER";
    public static final String WAITING = "waiting";
    public static final String NO_CODE = "No code";
    public static final String USER_UPLOAD_DIRECTORY = "/home/saletop/backend/uploads-user/profile-pictures/";
    public static final String USER_UPLOAD_DIRECTORY_URL = "/uploads-user/profile-pictures/";
    public static final String COMPANY_UPLOAD_DIRECTORY = "/home/saletop/backend/uploads-company/profile-pictures/";
    public static final String COMPANY_UPLOAD_DIRECTORY_POSTER = "/home/saletop/backend/uploads-company/poster-pictures/";
    public static final String COMPANY_UPLOAD_DIRECTORY_URL = "/uploads-company/profile-pictures/";
    public static final String COMPANY_UPLOAD_DIRECTORY_POSTER_URL = "/uploads-company/poster-pictures/";
    public static final String MAC_USER_UPLOAD_DIRECTORY = "uploads-user/profile-pictures/";
    public static final String MAC_COMPANY_UPLOAD_DIRECTORY = "uploads-company/profile-pictures/";
    public static final String MAC_COMPANY_UPLOAD_DIRECTORY_POSTER = "uploads-company/poster-pictures/";

    public static final String NewPosterAddedNotify = "NewPosterAddedNotify";
    public static final String ChangeBookmarkActive = "ChangeBookmarkActive";
    public static final String ChangeBookmarkActiveList = "ChangeBookmarkActiveList";
    public static final String DeleteBookmarkPromotionByPromotionId = "DeleteBookmarkPromotionByPromotionId";
    public static final String DeleteBookmarkPromotionByPromotionIdList = "DeleteBookmarkPromotionByPromotionIdList";
    public static final String DeleteUserFromSystem = "DeleteUserFromSystem";
    public static final String DeleteCompanyFromSystem = "DeleteCompanyFromSystem";
    public static final String DeletePromotionFromSystem = "DeletePromotionFromSystem";
    //public static final String SERVICE_SECRET_TOKEN = "ahQNwbQy9AdZvkpQWIu6ORC72WexY1lR9RC0xv3wp/eyfwM1Q14qO3xJGy";

    public Constant() {
    }
}
