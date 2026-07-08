package com.securevoting.entity;

public enum InvitationStatus {
    PENDING,    // created, not yet sent
    SENT,       // emailed to voter
    ACCEPTED,   // voter clicked link + signed in
    REVOKED,    // organizer cancelled
    EXPIRED     // election endTime passed
}