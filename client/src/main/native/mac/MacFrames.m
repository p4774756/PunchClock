#include <jni.h>
#include <stdlib.h>
#import <Cocoa/Cocoa.h>
#import <objc/runtime.h>

/*
 * 這個 JDK 的 AWTWindow 沒有覆寫 constrainFrameRect:toScreen:。
 * AppKit 因此不允許視窗上緣進入選單列：頭（留白）一貼到螢幕頂，整扇視窗就上不去。
 * 只放行標題以「洋流」開頭的無邊框主視窗，其餘對話框仍用系統原本的限制。
 */

static NSRect (*ns_constrain)(id, SEL, NSRect, NSScreen *);
static int installed;

static NSRect punchclock_constrain(id self, SEL cmd, NSRect rect, NSScreen *screen) {
    NSWindow *window = (NSWindow *)self;
    NSString *title = [window title];
    BOOL main = (title != nil) && [title hasPrefix:@"\u6d0b\u6d41"];
    BOOL borderless = ([window styleMask] & NSWindowStyleMaskTitled) == 0;
    if (main && borderless) {
        return rect;
    }
    if (ns_constrain != NULL) {
        return ns_constrain(self, cmd, rect, screen);
    }
    return rect;
}

static void punchclock_install_class(Class cls, SEL sel, const char *types) {
    if (cls == Nil) {
        return;
    }
    if (class_addMethod(cls, sel, (IMP)punchclock_constrain, types)) {
        return;
    }
    unsigned int count = 0;
    Method *list = class_copyMethodList(cls, &count);
    for (unsigned int i = 0; i < count; i++) {
        if (method_getName(list[i]) == sel) {
            method_setImplementation(list[i], (IMP)punchclock_constrain);
        }
    }
    free(list);
}

JNIEXPORT void JNICALL Java_com_example_ui_MacFrames_allowOverflow(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    if (installed) {
        return;
    }
    installed = 1;
    SEL sel = @selector(constrainFrameRect:toScreen:);
    Method base = class_getInstanceMethod([NSWindow class], sel);
    if (base == NULL) {
        return;
    }
    ns_constrain = (NSRect (*)(id, SEL, NSRect, NSScreen *))method_getImplementation(base);
    const char *types = method_getTypeEncoding(base);
    punchclock_install_class(objc_getClass("AWTWindow_Normal"), sel, types);
    punchclock_install_class(objc_getClass("AWTWindow_Panel"), sel, types);
}
