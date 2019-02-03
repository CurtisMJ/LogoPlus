#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>

#define FADERPATH "/sys/class/leds/lp5523:channel0/device/master_fader1"
#define E1PATH "/sys/class/leds/lp5523:channel0/device/engine1_mode"
#define E2PATH "/sys/class/leds/lp5523:channel0/device/engine2_mode"
#define E3PATH "/sys/class/leds/lp5523:channel0/device/engine3_mode"
int main()
{
    // get current fade
   FILE* fadefile = fopen(FADERPATH, "r");
    char buf[8];
   fread(buf, sizeof(buf) - 1, 1, fadefile);
    fclose(fadefile);
    int max;
    if (sscanf(buf, "%d", &max) != 1) max = 128;

    // fade out
    fopen(FADERPATH, "w");
    for (int i = max; i >= 0; --i)
    {
        usleep(5000);
        fprintf(fadefile, "%d", i);
        fflush(fadefile);
    }
   fclose(fadefile);

    // stop engines
    FILE* efile = fopen(E1PATH, "w");
    fprintf(efile, "disabled");
    fclose(efile);
    efile = fopen(E2PATH, "w");
    fprintf(efile, "disabled");
    fclose(efile);
    efile = fopen(E3PATH, "w");
    fprintf(efile, "disabled");
    fclose(efile);

   return 0;
}