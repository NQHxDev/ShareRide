package com.shareride.gateway.dto;

import lombok.Data;

@Data
public class LoginRequestDto {
    private String email;
    private String password;
    private String deviceId;
    private String deviceName;
}
