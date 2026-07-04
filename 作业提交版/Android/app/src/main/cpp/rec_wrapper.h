#ifndef REC_WRAPPER_H_
#define REC_WRAPPER_H_

#include <string>

struct RecWrapperImpl;

class RecWrapper {
public:
    RecWrapper();
    ~RecWrapper();

    bool init(const std::string& modelPath, const std::string& labelPath);
    std::string recognize(const unsigned char* bgrData, int width, int height, int stride);
    void release();

private:
    RecWrapperImpl* impl_;
};

#endif
