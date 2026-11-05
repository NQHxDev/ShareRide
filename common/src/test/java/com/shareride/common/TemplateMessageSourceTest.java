package com.shareride.common;

import com.shareride.common.constant.ResponseCode;
import com.shareride.common.i18n.SupportedLocale;
import com.shareride.common.i18n.TemplateMessageSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateMessageSourceTest {

   private TemplateMessageSource messageSource;

   @BeforeEach
   void setUp() {
      messageSource = new TemplateMessageSource();
      messageSource.init();
   }

   @Test
   @DisplayName("Should resolve standard SUCCESS message in all 4 languages")
   void testStandardSuccessMessageAllLocales() {
      assertEquals("Thành công",
            messageSource.getMessage(ResponseCode.SUCCESS.getCode(), SupportedLocale.VI.getLocale()));
      assertEquals("Success", messageSource.getMessage(ResponseCode.SUCCESS.getCode(), SupportedLocale.EN.getLocale()));
      assertEquals("操作成功", messageSource.getMessage(ResponseCode.SUCCESS.getCode(), SupportedLocale.ZH.getLocale()));
      assertEquals("成功しました", messageSource.getMessage(ResponseCode.SUCCESS.getCode(), SupportedLocale.JA.getLocale()));
   }

   @Test
   @DisplayName("Should interpolate dynamic parameters in FTL templates")
   void testParameterizedTemplate() {
      Map<String, Object> params = Map.of(
            "resource", "Người dùng",
            "identifier", "USR-12345");

      String viMsg = messageSource.getMessage("RESOURCE_NOT_FOUND", params, SupportedLocale.VI.getLocale());
      assertEquals("Không tìm thấy Người dùng với mã 'USR-12345'", viMsg);

      Map<String, Object> enParams = Map.of(
            "resource", "User",
            "identifier", "USR-12345");
      String enMsg = messageSource.getMessage("RESOURCE_NOT_FOUND", enParams, SupportedLocale.EN.getLocale());
      assertEquals("Could not find User with identifier 'USR-12345'", enMsg);

      Map<String, Object> jaParams = Map.of(
            "resource", "ユーザー",
            "identifier", "USR-12345");
      String jaMsg = messageSource.getMessage("RESOURCE_NOT_FOUND", jaParams, SupportedLocale.JA.getLocale());
      assertEquals("ユーザー（ID: 'USR-12345'）が見つかりません", jaMsg);

      Map<String, Object> zhParams = Map.of(
            "resource", "用户",
            "identifier", "USR-12345");
      String zhMsg = messageSource.getMessage("RESOURCE_NOT_FOUND", zhParams, SupportedLocale.ZH.getLocale());
      assertEquals("未找到用户，标识符：'USR-12345'", zhMsg);
   }

   @Test
   @DisplayName("Should fallback to default VI when requested locale is unsupported")
   void testFallbackLocale() {
      String msg = messageSource.getMessage(ResponseCode.UNAUTHORIZED.getCode(), Locale.FRENCH);
      assertEquals("Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn", msg);
   }

}
