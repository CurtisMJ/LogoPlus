//
// Created by curtisj on 2/3/19.
//

#include <sys/types.h>
#include <assert.h>
#include <sys/stat.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <errno.h>
#include <dirent.h>
#include <unistd.h>
#include <string.h>
#include <time.h>

#define SOCKET_PATH "14773833519149dc957ac9606d7f369f"
#define ABS_SOCKET_LEN(sun) (sizeof(sun->sun_family) + strlen(sun->sun_path + 1) + 1)

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

void error(char *fmt, ...)
{
    va_list args;
    va_start(args, fmt);
    fprintf(stderr, fmt, args);
    va_end(args);
    fprintf(stderr, "Error, abort. %d\n", errno);
    exit(EXIT_FAILURE);
}

socklen_t setup_sockaddr(struct sockaddr_un *sun, const char *name) {
    memset(sun, 0, sizeof(*sun));
    sun->sun_family = AF_LOCAL;
    strcpy(sun->sun_path + 1, name);
    return ABS_SOCKET_LEN(sun);
}

int acceptTimeout(int s, int timeout) {
    int iResult;
    struct timeval tv;
    fd_set rfds;
    FD_ZERO(&rfds);
    FD_SET(s, &rfds);

    tv.tv_sec = (long) timeout;
    tv.tv_usec = 0;

    iResult = select(s + 1, &rfds, (fd_set *) 0, (fd_set *) 0, &tv);
    if (iResult > 0) {
        return accept(s, NULL, NULL);
    }

    return -1;
}

void daemonRun() {

    int sockfd, newsockfd, servlen;
    struct sockaddr_un serv_addr;

    if ((sockfd = socket(AF_LOCAL, SOCK_STREAM | SOCK_CLOEXEC, 0)) < 0)
        error("socket() socket open failed\n");

    servlen = setup_sockaddr(&serv_addr, SOCKET_PATH);

    if (bind(sockfd, (struct sockaddr *) &serv_addr, servlen) < 0)
        error("bind() socket bind failed\n");

    if (listen(sockfd, 1) == -1) error("listen failed\n");

    newsockfd = acceptTimeout(sockfd, 10);

    if (newsockfd < 0) error("accept socket failed\n");

    FILE* ledFiles[9];
    int i;
    for (i = 0; i < 9; i++)
    {
        ledFiles[i] = fopen(leds[i], "w");
        if (ledFiles[i] == NULL) error("failed open %s\n", leds[i]);
        if (fprintf(ledFiles[i], "0") < 0) error("printf initial blank %s\n", leds[i]);
        if (fflush(ledFiles[i]) == EOF) error("flush %s\n", leds[i]);
    }
    while (1)
    {
        char cmd[2];
        char ledsCmd[10];
        char ack[1] = { 42 };
        if (read(newsockfd, cmd, 1) < 1) goto breakout;
        switch  (cmd[0])
        {
            case PUT:
                if (read(newsockfd, ledsCmd, 9) < 1) goto breakout;
                for (i = 0; i < 9; i++)
                {
                    if (fprintf(ledFiles[i], "%d", ledsCmd[i]) < 0)  error("printf put  %s\n", leds[i]);
                }
                for (i = 0; i < 9; i++)
                {
                    if (fflush(ledFiles[i]) == EOF) error("put flush %s\n", leds[i]);
                }
                break;
            case EXIT:
            default:
                goto breakout;
        }
    }
    breakout:;
    close(newsockfd);
    for (i = 0; i < 9; i++)
    {
        fclose(ledFiles[i]);
    }
}

int main()
{
    int pid = fork();

    if (pid < 0) {
        error("fork() Daemon start failed.");
    }

    if (pid == 0)
    {
        daemonRun();
        return 0;
    }

    printf("%d\n", pid);

    return 0;
}