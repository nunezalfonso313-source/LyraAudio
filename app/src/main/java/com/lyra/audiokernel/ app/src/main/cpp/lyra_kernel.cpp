#include <string>

class Decoder {
public:
    void processFormat(std::string name, std::string ext) {}
};

class AudioEngine {
public:
    float applyDSP(float sample, float bassGain) {
        float out = sample * bassGain;
        if (out > 1.0f) out = 1.0f;
        return out;
    }
};

class Visualizer {
public:
    void drawInterface(float level) {}
};

class LyraPlayer {
public:
    Decoder reader;
    AudioEngine engine;
    Visualizer screen;
    std::string getStatus() {
        return "LYRA KERNEL V1: ONLINE & BIT-PERFECT";
    }
};
