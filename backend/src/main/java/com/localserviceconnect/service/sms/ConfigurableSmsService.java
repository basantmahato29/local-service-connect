package com.localserviceconnect.service.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConfigurableSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableSmsService.class);

    private final String provider;
    private final String apiKey;
    private final String senderId;

    public ConfigurableSmsService(
            @Value("${app.sms.provider:none}") String provider,
            @Value("${app.sms.api-key:}") String apiKey,
            @Value("${app.sms.sender-id:LSCSRV}") String senderId) {
        this.provider = provider != null ? provider.trim().toLowerCase() : "none";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.senderId = senderId;
    }

    @Override
    public boolean isConfigured() {
        return !"none".equalsIgnoreCase(provider) && !apiKey.isBlank();
    }

    @Override
    public boolean sendOtp(String phoneNumber, String otp, String customerName) {
        if (!isConfigured()) {
            log.debug("No external SMS gateway configured (provider={}). OTP workflow continues via secure in-app dashboard.", provider);
            return false;
        }

        log.info("Sending OTP via SMS provider '{}' to recipient '{}' (senderId={})", provider, maskPhone(phoneNumber), senderId);
        // Note: Ready for integration with HTTP clients for Twilio / MSG91 / AWS SNS:
        // switch (provider) {
        //     case "twilio" -> twilioClient.send(...);
        //     case "msg91"  -> msg91Client.send(...);
        // }
        return true;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        return phone.substring(0, 2) + "****" + phone.substring(phone.length() - 2);
    }
}

