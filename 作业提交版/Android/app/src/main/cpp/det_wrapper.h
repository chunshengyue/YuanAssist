#ifndef DET_WRAPPER_H_
#define DET_WRAPPER_H_

#include <string>
#include <vector>

struct DetBox {
    int left;
    int top;
    int right;
    int bottom;
    float score;
};

struct DetWrapperImpl;

class DetWrapper {
public:
    DetWrapper();
    ~DetWrapper();

    bool init(const std::string& modelPath);
    std::vector<DetBox> detect(const unsigned char* bgrData, int width, int height, int stride);
    void release();

private:
    DetWrapperImpl* impl_;
};

#endif
