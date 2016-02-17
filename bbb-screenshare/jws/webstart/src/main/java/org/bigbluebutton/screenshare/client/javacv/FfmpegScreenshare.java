package org.bigbluebutton.screenshare.client.javacv;

import static org.bytedeco.javacpp.avcodec.AV_CODEC_ID_FLASHSV2;
import static org.bytedeco.javacpp.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.javacpp.avutil.AV_PIX_FMT_BGR24;
import static org.bytedeco.javacpp.avutil.AV_PIX_FMT_YUV420P;
import java.awt.AWTException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.bigbluebutton.screenshare.client.ScreenShareInfo;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

public class FfmpegScreenshare {
  private volatile boolean startBroadcast = false;
  private final Executor startBroadcastExec = Executors.newSingleThreadExecutor();
  private Runnable startBroadcastRunner; 
  private FFmpegFrameRecorder recorder = null;
  private Double defaultFrameRate = 12.0;
  private Double frameRate = 12.0;
  private int defaultKeyFrameInterval = 6;
  
  private long startTime;
  private int frameNumber = 1;
  
  private ScreenShareInfo ssi;
  private FFmpegFrameGrabber grabber;

  private final String FRAMERATE_KEY = "frameRate";
  private final String KEYFRAMEINTERVAL_KEY = "keyFrameInterval";
  
  public FfmpegScreenshare(ScreenShareInfo ssi) {
    this.ssi = ssi;
  }
  
  public void setCaptureCoordinates(int x, int y){
    // do nothing. Shoudl remove. 
  }
  
  private Map<String, String> splitToMap(String source, String entriesSeparator, String keyValueSeparator) {
    System.out.println("CODEC_OPTS=" + source);
      Map<String, String> map = new HashMap<String, String>();
      String[] entries = source.split(entriesSeparator);
      for (String entry : entries) {
          if (entry != "" && entry.contains(keyValueSeparator)) {
              String[] keyValue = entry.split(keyValueSeparator);
              System.out.println("OPTION: " + keyValue[0] + "=" + keyValue[1]);
              map.put(keyValue[0], keyValue[1]);
          }
      }
      return map;
  }
  
  public void go(String URL, int x, int y, int width, int height) throws IOException, BBBFrameRecorder.Exception, 
                  AWTException, InterruptedException {
  
    System.out.println("Java temp dir : " + System.getProperty("java.io.tmpdir"));
    System.out.println("Java name : " + System.getProperty("java.vm.name"));
    System.out.println("OS name : " + System.getProperty("os.name"));
    System.out.println("OS arch : " + System.getProperty("os.arch"));
    System.out.println("JNA Path : " + System.getProperty("jna.library.path"));
    System.out.println("Platform : " + Loader.getPlatform());
    System.out.println("Capturing w=[" + width + "] h=[" + height + "] at x=[" + x + "] y=[" + y + "]");
    
    Map<String, String> codecOptions = splitToMap(ssi.codecOptions, "&", "=");
    Double frameRate = parseFrameRate(codecOptions.get(FRAMERATE_KEY));
    
    String platform = Loader.getPlatform();
    String osName = System.getProperty("os.name").toLowerCase();
    if (platform.startsWith("windows")) {
      grabber = setupWindowsGrabber(width, height, x, y);
    } else if (osName.startsWith("linux")) {
      grabber = setupLinuxGrabber(width, height, x, y);
    } else if (platform.startsWith("macosx")) {
      grabber = setupMacOsXGrabber(width, height, x, y);
    }
    

    grabber.setFrameRate(frameRate);
    try {
      grabber.start();
    } catch (org.bytedeco.javacv.FrameGrabber.Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    recorder = new FFmpegFrameRecorder(URL, grabber.getImageWidth(), grabber.getImageHeight());
    
    useH264(recorder, codecOptions);
    
    startTime = System.currentTimeMillis();
    
    try {
      recorder.start();
    } catch (Exception e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
  }
  
  private Double parseFrameRate(String value) {
    Double fr = defaultFrameRate; 
        
    try {
      fr = Double.parseDouble(value);
    } catch (NumberFormatException e) {
      fr = defaultFrameRate; 
    }
        
    return fr;
  }
  
  private int parseKeyFrameInterval(String value) {
    int fr = defaultKeyFrameInterval; 
        
    try {
      fr = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      fr = defaultKeyFrameInterval; 
    }
        
    return fr;
  }
  
  private void captureScreen() {
    long now = System.currentTimeMillis();

    Frame frame;
    try {
      frame = grabber.grabImage();
      if (frame != null) {
        try {
          recorder.record(frame);
        } catch (Exception e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    } catch (Exception e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }


    long sleepFramerate = (long) (1000 / frameRate);
    long timestamp = now - startTime;
    recorder.setTimestamp(timestamp * 1000);

    //        System.out.println("i=[" + i + "] timestamp=[" + timestamp + "]");
    recorder.setFrameNumber(frameNumber);

//    System.out.println("[ENCODER] encoded image " + frameNumber + " in " + (System.currentTimeMillis() - now));
    frameNumber++;

    long execDuration = (System.currentTimeMillis() - now);
    long sleepDuration = Math.max(sleepFramerate - execDuration, 0);
    pause(sleepDuration);

  }
  
  private void pause(long dur) {
    try{
      Thread.sleep(dur);
    } catch (Exception e){
      System.out.println("Exception sleeping.");
    }
  }

  public void start() {
    startBroadcast = true;
    startBroadcastRunner =  new Runnable() {
      public void run() {
        while (startBroadcast){
          captureScreen();
        }
        System.out.println("Stopping screen capture.");     
      }
    };
    startBroadcastExec.execute(startBroadcastRunner);    
  }

  public void stop() {
    startBroadcast = false;
    if (recorder != null) {

      try {
        recorder.stop();
        recorder.release();
        grabber.stop();
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }
  
  private void useH264(FFmpegFrameRecorder recorder, Map<String, String> codecOptions) {
    Double frameRate = parseFrameRate(codecOptions.get(FRAMERATE_KEY));
    recorder.setFrameRate(frameRate);
    
    int keyFrameInterval =  parseKeyFrameInterval(codecOptions.get(KEYFRAMEINTERVAL_KEY));
    int gopSize = frameRate.intValue() * keyFrameInterval;
    recorder.setGopSize(gopSize);
    
    System.out.println("==== CODEC OPTIONS =====");
    for (Map.Entry<String, String> entry : codecOptions.entrySet()) {
      System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue());
      if (entry.getKey().equals(FRAMERATE_KEY) || entry.getKey().equals(KEYFRAMEINTERVAL_KEY)) {
        // ignore as we have handled this above
      } else {
        recorder.setVideoOption(entry.getKey(), entry.getValue());        
      }

    }
    System.out.println("==== END CODEC OPTIONS =====");
    
    recorder.setFormat("flv");
      
    // H264
    recorder.setVideoCodec(AV_CODEC_ID_H264);
    recorder.setPixelFormat(AV_PIX_FMT_YUV420P);
    recorder.setVideoOption("crf", "38");
    recorder.setVideoOption("preset", "veryfast");
    recorder.setVideoOption("tune", "zerolatency");
    recorder.setVideoOption("intra-refresh", "1"); 
  }
  
  private void useSVC2(FFmpegFrameRecorder recorder) {
    recorder.setFormat("flv");
    
    ///
    // Flash SVC2
    recorder.setVideoCodec(AV_CODEC_ID_FLASHSV2);
    recorder.setPixelFormat(AV_PIX_FMT_BGR24);
    
  }
  
  // Need to construct our grabber depending on which
  // platform the user is using.
  // https://trac.ffmpeg.org/wiki/Capture/Desktop
  //
  private FFmpegFrameGrabber setupWindowsGrabber(int width, int height, int x, int y) {
    System.out.println("Setting up grabber for windows.");
    FFmpegFrameGrabber winGrabber = new FFmpegFrameGrabber("desktop");
    winGrabber.setImageWidth(width);
    winGrabber.setImageHeight(height);
    
    if (ssi.fullScreen) {
      winGrabber.setOption("offset_x", new Integer(0).toString());
      winGrabber.setOption("offset_y", new Integer(0).toString());      
    } else {
      winGrabber.setOption("offset_x", new Integer(x).toString());
      winGrabber.setOption("offset_y", new Integer(y).toString());       
    }
    winGrabber.setFormat("gdigrab");   
    
    return winGrabber;
  }
  
  private FFmpegFrameGrabber setupLinuxGrabber(int width, int height, int x, int y) {
    // ffmpeg -video_size 1024x768 -framerate 25 -f x11grab -i :0.0+100,200 output.mp4
    // This will grab the image from desktop, starting with the upper-left corner at (x=100, y=200) 
    // with the width and height of 1024x768.

    String inputDevice = ":"; 
    if (ssi.fullScreen) {
      inputDevice.concat(new Integer(0).toString()).concat(".").concat(new Integer(0).toString());
      inputDevice.concat("+").concat(new Integer(0).toString()).concat(",").concat(new Integer(0).toString());     
    } else {
      inputDevice.concat(new Integer(0).toString()).concat(".").concat(new Integer(0).toString());
      inputDevice.concat("+").concat(new Integer(x).toString()).concat(",").concat(new Integer(y).toString());      
    }
    
    String videoSize = new Integer(width).toString().concat("x").concat(new Integer(height).toString());
    
    System.out.println("Setting up grabber for linux.");
    System.out.println("input:" + inputDevice + " videoSize:" + videoSize);
    
    FFmpegFrameGrabber linuxGrabber = new FFmpegFrameGrabber(inputDevice);
    linuxGrabber.setImageWidth(width);
    linuxGrabber.setImageHeight(height);
    linuxGrabber.setOption("video_size", videoSize); 
    linuxGrabber.setFormat("x11grab");    
    return linuxGrabber;
  }
  
  private FFmpegFrameGrabber setupMacOsXGrabber(int width, int height, int x, int y) {
    
    //ffmpeg -f avfoundation -i "Capture screen 0" test.mkv
    String inputDevice = "Capture screen 0";     
    String videoSize = new Integer(width).toString().concat("x").concat(new Integer(height).toString());

    System.out.println("Setting up grabber for linux.");
    System.out.println("input:" + inputDevice + " videoSize:" + videoSize);
    
    FFmpegFrameGrabber macGrabber = new FFmpegFrameGrabber(inputDevice);
    macGrabber.setOption("video_size", videoSize); 
    macGrabber.setFormat("avfoundation");
    return macGrabber;
  }
}
