import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by plezerv on 2013.06.06..
 */
public class ScratchView extends SurfaceView implements SurfaceHolder.Callback {

    private Bitmap drawable;

    public void setDrawable(Bitmap drawable) {
        this.drawable = drawable;
        invalidate();
    }

    private ScratchViewThread mThread;

    List<Path> mPathList = new ArrayList<Path>();

    private Paint mOverlayPaint;

    private int mRevealSize;

    private boolean mIsScratchable = true;
    private boolean mIsAntiAlias = false;
    private Path path;
    private float startX = 0;
    private float startY = 0;
    private boolean mScratchStart = false;
    private int lastX = -1;
    private int lastY = -1;

    private boolean scratched;
    private boolean bitmap[][];
    private int revealed;

    public interface OnFullyScratchedListener {
        void onFullyScratched();
    }

    private OnFullyScratchedListener listener;

    public void setOnFullyScratchedListener(OnFullyScratchedListener listener) {
        this.listener = listener;
    }

    public ScratchView(Context context) {
        this(context, null);
    }

    public ScratchView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);

        mRevealSize = Globals.SCRATCH_REVEAL_SIZE;

        setZOrderOnTop(true);
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        holder.setFormat(PixelFormat.TRANSPARENT);

        mOverlayPaint = new Paint();
        mOverlayPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mOverlayPaint.setStyle(Paint.Style.STROKE);
        mOverlayPaint.setStrokeCap(Paint.Cap.ROUND);
        mOverlayPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void onDraw(Canvas canvas) {
        if(scratched) {
            canvas.drawColor( 0, PorterDuff.Mode.CLEAR );
            return;
        }

        Rect dest = new Rect(0, 0, getWidth(), getHeight());
        if(drawable != null ) canvas.drawBitmap(drawable, null, dest, new Paint());
        else canvas.drawColor(Color.BLACK);

        for(Path path: mPathList){
            mOverlayPaint.setAntiAlias(mIsAntiAlias);
            mOverlayPaint.setStrokeWidth(mRevealSize);

            canvas.drawPath(path, mOverlayPaint);
        }
    }

    private void markRadius(int x, int y) {
        int r = Globals.SCRATCH_REVEAL_SIZE / 2 + 1;

        for (int i = x - r; i <= x + r; i++) {
            for (int j = y - r; j <= y + r; j++) {
                if ( Math.pow(i - x, 2) + Math.pow(j - y, 2) <= Math.pow(r, 2)) {
                    if (i >= 0 && j >= 0 && i < getWidth() && j < getHeight() && !bitmap[i][j]) {
                        bitmap[i][j] = true;
                        revealed++;
                    }
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        synchronized (mThread.getSurfaceHolder()) {
            if (!mIsScratchable || scratched) {
                return true;
            }

            if(bitmap == null) {
                bitmap = new boolean[getWidth()][getHeight()];
                revealed = 0;
                scratched = false;
            }

            int x = (int)me.getX();
            int y = (int)me.getY();

            switch(me.getAction()){
                case MotionEvent.ACTION_DOWN:
                    path = new Path();
                    path.moveTo(me.getX(), me.getY());
                    startX = me.getX();
                    startY = me.getY();
                    mPathList.add(path);
                    markRadius(x,y);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if(mScratchStart){
                        path.lineTo(me.getX(), me.getY());
                    } else{
                        if(isScratch(startX, me.getX(), startY, me.getY())){
                            mScratchStart = true;
                            path.lineTo(me.getX(), me.getY());
                        }
                    }
                    if(mScratchStart && lastX != -1 && lastY != -1) {
                        float step = 0f;
                        boolean stopX = false;
                        boolean stopY = false;
                        int endX = x;
                        int endY = y;
                        while(!stopX || !stopY) {
                            step += 0.25f;
                            if(step > 1) step = 1;
                            int newX = (int)(lastX + (endX - lastX) * step);
                            if(newX == endX) stopX = true;
                            int newY = (int)(lastY + (endY - lastY) * step);
                            if(newY == endY) stopY = true;
                            markRadius(newX,newY);
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    mScratchStart = false;
                    markRadius(x,y);
                    break;
            }
            lastX = x;
            lastY = y;

            float ratio = revealed / (float)(getWidth() * getHeight());

            if(ratio > Globals.SCRATCH_RATIO && listener != null && !scratched) {
                listener.onFullyScratched();
                scratched = true;
                setEnabled(false);
            }
            return true;
        }
    }

    private boolean isScratch(float oldX, float x, float oldY, float y) {
        float distance = (float) Math.sqrt(Math.pow(oldX - x, 2) + Math.pow(oldY - y, 2));
        if(distance > mRevealSize * 2){
            return true;
        }else{
            return false;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0) {
        mThread = new ScratchViewThread(getHolder(), this);
        mThread.setRunning(true);
        mThread.start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        boolean retry = true;
        mThread.setRunning(false);
        while (retry) {
            try {
                mThread.join();
                retry = false;
            } catch (InterruptedException e) {
            }
        }

    }

    class ScratchViewThread extends Thread {
        private SurfaceHolder mSurfaceHolder;
        private ScratchView mView;
        private boolean mRun = false;

        public ScratchViewThread(SurfaceHolder surfaceHolder, ScratchView view) {
            mSurfaceHolder = surfaceHolder;
            mView = view;
        }

        public void setRunning(boolean run) {
            mRun = run;
        }

        public SurfaceHolder getSurfaceHolder() {
            return mSurfaceHolder;
        }

        @Override
        public void run() {
            Canvas c;
            while (mRun) {
                c = null;
                try {
                    c = mSurfaceHolder.lockCanvas(null);
                    synchronized (mSurfaceHolder) {
                        if(c != null){
                            mView.onDraw(c);
                        }
                    }
                } finally {
                    if (c != null) {
                        mSurfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            }
        }
    }

    public void resetView(){
        if(mThread != null) {
            synchronized (mThread.getSurfaceHolder()) {
                if(mPathList != null) mPathList.clear();
            }
        }
    }
}
