package android.app;

import android.content.Context;
import android.os.RemoteException;
import com.android.internal.app.IThsService;

public class ThsManager
{
    private final Context mContext;
    private final IThsService mService;

    ThsManager(Context paramContext, IThsService paramIThsService)
    {
        this.mContext = paramContext;
        this.mService = paramIThsService;
    }

    public void initUid(int paramInt)
    {
        try
        {
            this.mService.initUid(paramInt);
            return;
        }
        catch (RemoteException localRemoteException)
        {
             localRemoteException.printStackTrace();
        }
    }

    public void previewColor(int paramInt)
    {
        try
        {
            this.mService.previewColor(paramInt);
        }
        catch (RemoteException localRemoteException)
        {
            localRemoteException.printStackTrace();
        }
    }

    public void runThsEffect(int paramInt)
    {
        try
        {
            this.mService.runThsEffect(paramInt, -15728896);
            return;
        }
        catch (RemoteException localRemoteException)
        {
            localRemoteException.printStackTrace();
        }
    }

    public void runThsEffect(int paramInt1, int paramInt2)
    {
        try
        {
            this.mService.runThsEffect(paramInt1, paramInt2);
            return;
        }
        catch (RemoteException localRemoteException)
        {
            localRemoteException.printStackTrace();
        }
    }

    public void runThsProgram(String paramString1, String paramString2, String paramString3, String paramString4)
    {
        try
        {
            this.mService.runThsProgram(paramString1, paramString2, paramString3, paramString4);
            return;
        }
        catch (RemoteException localRemoteException)
        {
            localRemoteException.printStackTrace();
        }
    }

    public void setBrightness(int paramInt)
    {
        try
        {
            this.mService.setBrightness(paramInt);
            return;
        }
        catch (RemoteException localRemoteException)
        {
            localRemoteException.printStackTrace();
        }
    }

    public void suspendThs()
    {
        try
        {
            this.mService.suspendThs();
        }
        catch (RemoteException localRemoteException)
        {
            localRemoteException.printStackTrace();
        }
    }
}