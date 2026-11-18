package com.shareride.gateway.dto;

import lombok.Data;

@Data
public class RegisterRequestDto {
    private String email;
    private String password;
    private String phoneNumber;
    private String roleCode;
    private String displayName;
}
