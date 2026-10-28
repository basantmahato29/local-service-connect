package com.localserviceconnect.service.sms;

/**
 * Service interface for dispatching SMS notifications (e.g. OTP verification messages).
 */
public interface SmsService {

    /**
     * Sends an OTP SMS notification if an SMS provider is configured.
     *
     * @param phoneNumber recipient phone number
     * @param otp 4-digit OTP code
     * @param customerName name of customer receiving the OTP
     * @return true if sent, false if unconfigured or delivery failed
     */
    boolean sendOtp(String phoneNumber, String otp, String customerName);

    /**
     * Checks whether an external SMS gateway provider is currently configured.
     */
    boolean isConfigured();
}

