package com.example.pickme;

import static org.junit.Assert.*;


import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.pickme.models.Event;
import com.example.pickme.models.EventStatus;

import com.example.pickme.services.QRCodeScanner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Test Cases for user stories:
 * US 01.06.01：As an entrant, I want to view event details within the app by scanning the promotional QR code.
 * US 02.01.04：As an organizer, I want to set a registration period.
 */
@RunWith(AndroidJUnit4.class)
public class TestCase {

    private QRCodeScanner scanner;

    @Before
    public void setUp() {
        // Initialize scanner that internally uses Firebase and Android Log
        scanner = new QRCodeScanner();
    }


    // US 01.06.01


    @Test
    public void testExtractValidEventId() {
        String data = "eventlottery://event/EVT123";
        String result = scanner.extractEventId(data);
        assertEquals("EVT123", result);
    }

    @Test
    public void testInvalidQRCodeFormat() {
        String data = "https://example.com";
        String result = scanner.extractEventId(data);
        assertNull(result);
    }




    // US 02.01.04


    @Test
    public void testRegistrationPeriod_Valid() {
        Event event = new Event();
        long now = System.currentTimeMillis();

        event.setRegistrationStartDate(now);
        event.setRegistrationEndDate(now + 100000);
        event.setStatusEnum(EventStatus.OPEN);

        assertTrue("Registration should be open", event.isRegistrationOpen());
    }

    @Test
    public void testRegistrationPeriod_Invalid() {
        Event event = new Event();
        long now = System.currentTimeMillis();

        event.setRegistrationStartDate(now + 10000);
        event.setRegistrationEndDate(now);
        event.setStatusEnum(EventStatus.OPEN);

        assertFalse("Registration should be invalid when end < start",
                event.isRegistrationOpen());
    }
}

