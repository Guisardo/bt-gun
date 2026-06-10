#pragma once

#include <DriverKit/IOMemoryDescriptor.h>
#include <DriverKit/IOService.h>
#include <DriverKit/OSAction.h>
#include <HIDDriverKit/IOHIDDevice.h>
#include <HIDDriverKit/IOHIDDeviceTypes.h>
#include <stdint.h>

class BTGunHidDriver final : public IOHIDDevice
{
    OSDeclareDefaultStructors(BTGunHidDriver)

public:
    kern_return_t Start(IOService* provider) override;
    kern_return_t Stop(IOService* provider) override;

    kern_return_t handleReport(uint64_t timestamp,
                               IOMemoryDescriptor* report,
                               uint32_t reportLength,
                               IOHIDReportType reportType = kIOHIDReportTypeInput,
                               IOOptionBits options = 0) override;

    kern_return_t getReport(IOMemoryDescriptor* report,
                            IOHIDReportType reportType,
                            IOOptionBits options,
                            uint32_t completionTimeout,
                            OSAction* action) override;

    kern_return_t setReport(IOMemoryDescriptor* report,
                            IOHIDReportType reportType,
                            IOOptionBits options,
                            uint32_t completionTimeout,
                            OSAction* action) override;

    kern_return_t SubmitInputReportFromHost(IOMemoryDescriptor* report,
                                            const uint8_t* reportBytes,
                                            uint32_t reportLength,
                                            uint64_t timestamp);

private:
    static constexpr uint8_t kInputReportId = 0x01;
    static constexpr uint8_t kOutputReportId = 0x02;
    static constexpr uint8_t kOutputVersion = 0x01;
    static constexpr uint32_t kInputReportLength = 10;
    static constexpr uint32_t kOutputReportLength = 9;

    uint8_t lastOutputReport_[kOutputReportLength];
    bool outputReportSeen_;

    static bool IsValidInputReport(const uint8_t* reportBytes, uint32_t reportLength);
    static bool IsValidOutputReport(const uint8_t* reportBytes, uint32_t reportLength);
    static kern_return_t CopyFromDescriptor(IOMemoryDescriptor* descriptor,
                                            uint8_t* destination,
                                            uint32_t destinationLength);
    static kern_return_t CopyToDescriptor(IOMemoryDescriptor* descriptor,
                                          const uint8_t* source,
                                          uint32_t sourceLength);
};
