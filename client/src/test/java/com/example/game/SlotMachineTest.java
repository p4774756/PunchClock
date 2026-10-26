package com.example.game;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class SlotMachineTest {

    @Test
    public void payTableUsesFixedMultipliers() {
        assertEquals(20, SlotMachine.multiplier(new String[] {"紅七", "紅七", "紅七"}));
        assertEquals(10, SlotMachine.multiplier(new String[] {"星星", "星星", "星星"}));
        assertEquals(6, SlotMachine.multiplier(new String[] {"鈴鐺", "鈴鐺", "鈴鐺"}));
        assertEquals(4, SlotMachine.multiplier(new String[] {"檸檬", "檸檬", "檸檬"}));
        assertEquals(3, SlotMachine.multiplier(new String[] {"櫻桃", "櫻桃", "櫻桃"}));
        assertEquals(2, SlotMachine.multiplier(new String[] {"櫻桃", "檸檬", "櫻桃"}));
        assertEquals(0, SlotMachine.multiplier(new String[] {"檸檬", "鈴鐺", "星星"}));
    }

    @Test
    public void spinFollowsTheRandomDraw() {
        SlotMachine.Result result = SlotMachine.spin(new Random() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        });
        assertEquals("櫻桃", result.reels[0]);
        assertEquals(3, result.multiplier);
        assertEquals(300, result.returned(100));
    }
}
