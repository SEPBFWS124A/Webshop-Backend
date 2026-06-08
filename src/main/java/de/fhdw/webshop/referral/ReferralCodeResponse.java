package de.fhdw.webshop.referral;

public record ReferralCodeResponse(
        String code,
        long referralCount
) {}
