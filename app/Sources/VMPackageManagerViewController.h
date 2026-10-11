#import <UIKit/UIKit.h>
@class VMUSBTransport;
@interface VMPackageManagerViewController : UITabBarController
- (instancetype)initWithTransport:(VMUSBTransport *)transport instanceID:(NSString *)identifier;
- (instancetype)initWithTransport:(VMUSBTransport *)transport instanceID:(NSString *)identifier
                  finishingSetup:(BOOL)finishingSetup;
@property (nonatomic, copy) void (^setupRequest)(void);
@end
