package com.shareride.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RenderedEmail {
    private String htmlBody;
    private String plainTextBody;
    private String subject;
}
