package com.ninimum.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
public class TokenDto {
	private Long userSeq;
	private String grantType;
	private String accessToken;
	private String refreshToken;
	private String pcAccessToken; //Desktop
	private String mbAccessToken; //Mobile

	public TokenDto() {

	}
}
