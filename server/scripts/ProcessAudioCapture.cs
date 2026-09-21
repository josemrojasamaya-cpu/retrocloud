using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;

[ComImport, Guid("72A22D78-CDE4-431D-B8CC-843A71199B6D"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IActivateAudioInterfaceAsyncOperation
{
    void GetActivateResult(out int activateResult, [MarshalAs(UnmanagedType.IUnknown)] out object activatedInterface);
}

[ComImport, Guid("41D949AB-9862-444A-80F6-C261334DA5EB"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IActivateAudioInterfaceCompletionHandler
{
    void ActivateCompleted(IActivateAudioInterfaceAsyncOperation activateOperation);
}

[ComImport, Guid("1CB9AD4C-DBFA-4c32-B178-C2F568A703B2"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IAudioClient
{
    int Initialize(int shareMode, uint streamFlags, long hnsBufferDuration, long hnsPeriodicity, IntPtr pFormat, IntPtr audioSessionGuid);
    int GetBufferSize(out uint pNumBufferFrames);
    int GetStreamLatency(out long phnsLatency);
    int GetCurrentPadding(out uint pNumPaddingFrames);
    int IsFormatSupported(int shareMode, IntPtr pFormat, out IntPtr ppClosestMatch);
    int GetMixFormat(out IntPtr ppDeviceFormat);
    int GetDevicePeriod(out long phnsDefaultDevicePeriod, out long phnsMinimumDevicePeriod);
    int Start();
    int Stop();
    int Reset();
    int SetEventHandle(IntPtr eventHandle);
    int GetService([In] ref Guid riid, [MarshalAs(UnmanagedType.IUnknown)] out object ppv);
}

[ComImport, Guid("C8ADBD64-E71E-48a0-A4DE-185C395CD317"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IAudioCaptureClient
{
    int GetBuffer(out IntPtr ppData, out uint pNumFramesAvailable, out uint pdwFlags, out long pu64DevicePosition, out long pu64QPCPosition);
    int ReleaseBuffer(uint numFramesRead);
    int GetNextPacketSize(out uint pNumFramesInNextPacket);
}

[StructLayout(LayoutKind.Sequential)]
struct WAVEFORMATEX
{
    public ushort wFormatTag;
    public ushort nChannels;
    public uint nSamplesPerSec;
    public uint nAvgBytesPerSec;
    public ushort nBlockAlign;
    public ushort wBitsPerSample;
    public ushort cbSize;
}

[StructLayout(LayoutKind.Sequential)]
struct AUDIOCLIENT_PROCESS_LOOPBACK_PARAMS
{
    public uint TargetProcessId;
    public int ProcessLoopbackMode;
}

[StructLayout(LayoutKind.Sequential)]
struct AUDIOCLIENT_ACTIVATION_PARAMS
{
    public int ActivationType;
    public AUDIOCLIENT_PROCESS_LOOPBACK_PARAMS ProcessLoopbackParams;
}

[StructLayout(LayoutKind.Sequential)]
struct BLOB
{
    public uint cbSize;
    public IntPtr pBlobData;
}

[StructLayout(LayoutKind.Explicit)]
struct PROPVARIANT
{
    [FieldOffset(0)] public ushort vt;
    [FieldOffset(8)] public BLOB blob;
}

class CompletionHandler : IActivateAudioInterfaceCompletionHandler
{
    public ManualResetEvent Done = new ManualResetEvent(false);
    public IActivateAudioInterfaceAsyncOperation Op;
    public void ActivateCompleted(IActivateAudioInterfaceAsyncOperation op) { Op = op; Done.Set(); }
}

class Program
{
    [DllImport("Mmdevapi.dll")]
    static extern int ActivateAudioInterfaceAsync(
        [MarshalAs(UnmanagedType.LPWStr)] string deviceInterfacePath,
        [In, MarshalAs(UnmanagedType.LPStruct)] Guid riid,
        ref PROPVARIANT activationParams,
        IActivateAudioInterfaceCompletionHandler completionHandler,
        out IntPtr activationOperation);

    static void Err(string msg) { Console.Error.WriteLine("{\"error\":\"" + msg + "\"}"); Console.Error.Flush(); }

    static int Main(string[] args)
    {
        if (args.Length < 1) { Err("Usage: ProcessAudioCapture.exe <pid>"); return 1; }
        uint pid;
        if (!uint.TryParse(args[0], out pid)) { Err("PID invalido"); return 1; }

        var activParams = new AUDIOCLIENT_ACTIVATION_PARAMS();
        activParams.ActivationType = 1;
        activParams.ProcessLoopbackParams.TargetProcessId = pid;
        activParams.ProcessLoopbackParams.ProcessLoopbackMode = 0;

        int paramSize = Marshal.SizeOf(typeof(AUDIOCLIENT_ACTIVATION_PARAMS));
        IntPtr paramPtr = Marshal.AllocHGlobal(paramSize);
        Marshal.StructureToPtr(activParams, paramPtr, false);

        var pv = new PROPVARIANT();
        pv.vt = 0x0041; // VT_BLOB
        pv.blob.cbSize = (uint)paramSize;
        pv.blob.pBlobData = paramPtr;

        var handler = new CompletionHandler();
        IntPtr asyncOp;
        Guid iidAudioClient = typeof(IAudioClient).GUID;

        int hr = ActivateAudioInterfaceAsync("VAD\\Process_Loopback", iidAudioClient, ref pv, handler, out asyncOp);
        if (hr < 0) { Err("ActivateAudioInterfaceAsync: 0x" + hr.ToString("X8")); Marshal.FreeHGlobal(paramPtr); return 1; }

        handler.Done.WaitOne(10000);
        Marshal.FreeHGlobal(paramPtr);

        if (handler.Op == null) { Err("Timeout activando audio"); return 1; }

        int activateResult;
        object iface;
        handler.Op.GetActivateResult(out activateResult, out iface);
        if (activateResult < 0) { Err("Activate result: 0x" + activateResult.ToString("X8")); return 1; }

        IAudioClient audioClient = (IAudioClient)iface;

        IntPtr mixFormatPtr;
        audioClient.GetMixFormat(out mixFormatPtr);
        WAVEFORMATEX mixFormat = (WAVEFORMATEX)Marshal.PtrToStructure(mixFormatPtr, typeof(WAVEFORMATEX));

        ushort ch = (ushort)Math.Min(mixFormat.nChannels, (ushort)2);
        var captureFormat = new WAVEFORMATEX();
        captureFormat.wFormatTag = 1;
        captureFormat.nChannels = ch;
        captureFormat.nSamplesPerSec = mixFormat.nSamplesPerSec;
        captureFormat.wBitsPerSample = 16;
        captureFormat.nBlockAlign = (ushort)(ch * 2);
        captureFormat.nAvgBytesPerSec = mixFormat.nSamplesPerSec * (uint)ch * 2;
        captureFormat.cbSize = 0;

        IntPtr fmtPtr = Marshal.AllocHGlobal(Marshal.SizeOf(typeof(WAVEFORMATEX)));
        Marshal.StructureToPtr(captureFormat, fmtPtr, false);

        // AUDCLNT_STREAMFLAGS_LOOPBACK | AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM | AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY
        uint flags = 0x00020000u | 0x80000000u | 0x08000000u;
        hr = audioClient.Initialize(0, flags, 200000, 0, fmtPtr, IntPtr.Zero);
        Marshal.FreeHGlobal(fmtPtr);
        if (hr < 0) { Err("Initialize: 0x" + hr.ToString("X8")); return 1; }

        Guid iidCapture = typeof(IAudioCaptureClient).GUID;
        object capObj;
        audioClient.GetService(ref iidCapture, out capObj);
        IAudioCaptureClient captureClient = (IAudioCaptureClient)capObj;

        Console.Error.WriteLine("{\"ready\":true,\"sampleRate\":" + captureFormat.nSamplesPerSec + ",\"channels\":" + captureFormat.nChannels + ",\"encoding\":\"pcm_s16le\"}");
        Console.Error.Flush();

        audioClient.Start();

        Stream stdout = Console.OpenStandardOutput();
        byte[] silence = null;

        while (true)
        {
            Thread.Sleep(10);
            uint nextSize;
            while (captureClient.GetNextPacketSize(out nextSize) == 0 && nextSize > 0)
            {
                IntPtr dataPtr;
                uint numFrames, bufFlags;
                long devPos, qpcPos;
                if (captureClient.GetBuffer(out dataPtr, out numFrames, out bufFlags, out devPos, out qpcPos) != 0) break;
                int byteCount = (int)(numFrames * captureFormat.nBlockAlign);
                if (byteCount > 0)
                {
                    if ((bufFlags & 2) != 0) // AUDCLNT_BUFFERFLAGS_SILENT
                    {
                        if (silence == null || silence.Length < byteCount) silence = new byte[byteCount];
                        try { stdout.Write(silence, 0, byteCount); } catch { return 0; }
                    }
                    else
                    {
                        byte[] buf = new byte[byteCount];
                        Marshal.Copy(dataPtr, buf, 0, byteCount);
                        try { stdout.Write(buf, 0, byteCount); } catch { return 0; }
                    }
                }
                captureClient.ReleaseBuffer(numFrames);
            }
        }
    }
}
