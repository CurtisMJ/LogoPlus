// IThsService.aidl
package com.android.internal.app;

// Declare any non-default types here with import statements

interface IThsService {
   void initUid(int paramInt);

   void previewColor(int paramInt);

  void runThsEffect(int paramInt1, int paramInt2);

  void runThsProgram(String paramString1, String paramString2, String paramString3, String paramString4);

  void setBrightness(int paramInt);

  void suspendThs();

}
