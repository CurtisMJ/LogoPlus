//
// Created by curtisj on 2/3/19.
//

#include <stdio.h>
#include <unistd.h>

#define  PUT 0
#define  EXIT 1

char leds[9][43] =   {
        "/sys/class/leds/lp5523:channel0/brightness",
        "/sys/class/leds/lp5523:channel1/brightness",
        "/sys/class/leds/lp5523:channel2/brightness",
        "/sys/class/leds/lp5523:channel3/brightness",
        "/sys/class/leds/lp5523:channel4/brightness",
        "/sys/class/leds/lp5523:channel5/brightness",
        "/sys/class/leds/lp5523:channel6/brightness",
        "/sys/class/leds/lp5523:channel7/brightness",
        "/sys/class/leds/lp5523:channel8/brightness"
};

int main()
{
    FILE* ledFiles[9];
    int i;
    for (i = 0; i < 9; i++)
    {
        ledFiles[i] = fopen(leds[i], "w");
        fprintf(ledFiles[i], "0");
        fflush(ledFiles[i]);
    }
    while (1)
    {
        int cmd = getchar();
        char ledsCmd[10];
        switch  (cmd)
        {
            case PUT:
                fgets(ledsCmd, 9, stdin);
                for (i = 0; i < 9; i++)
                {
                    fprintf(ledFiles[i], "%d", ledsCmd[i]);
                }
                for (i = 0; i < 9; i++)
                {
                    fflush(ledFiles[i]);
                }
                break;
            case EXIT:
            default:
                goto breakout;
        }
    }
    breakout:;
    for (i = 0; i < 9; i++)
    {
        fclose(ledFiles[i]);
    }
    return 0;
}