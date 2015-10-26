/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.appointment;

import android.annotation.SuppressLint;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.Layout.Alignment;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.format.Time;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.EdgeEffect;
import android.widget.OverScroller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.appointment.AppointmentView.MessageHandler.EventMessage;

public class AppointmentView extends View {
    private static String TAG = "DayView";
    private static boolean DEBUG = false;
    
    private static final String[] s24Hours = { "00:00", "01:00", "02:00", "03:00", "04:00", "05:00",
        "06:00", "07:00", "08:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00",
        "17:00", "18:00", "19:00", "20:00", "21:00", "22:00", "23:00", "00:00" };

    // duration to show the event clicked
    private static final int CLICK_DISPLAY_DURATION = 50;

    private static int DEFAULT_CELL_HEIGHT = 100;
    
    private Handler mHandler;

    protected Context mContext;

    // Make this visible within the package for more informative debugging
    Time mBaseDate;
    private Time mCurrentTime;
    //Update the current time line every five minutes if the window is left open that long
    private static final int UPDATE_CURRENT_TIME_DELAY = 300000;
    private final UpdateCurrentTime mUpdateCurrentTime = new UpdateCurrentTime();
    private int mTodayJulianDay;
    private int mCurrentSelectedJulianDay;

    private final Typeface mBold = Typeface.DEFAULT_BOLD;
    private Event mClickedEvent;           // The event the user clicked on
    private Event mSavedClickedEvent;
    private static int mOnDownDelay;
    private long mDownTouchTime;

    private int mEventsAlpha = 255;

    protected static StringBuilder mStringBuilder = new StringBuilder(50);
    protected static Formatter mFormatter = new Formatter(mStringBuilder, Locale.getDefault());

    private final Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            String tz = CalendarUtils.getTimeZone(mContext, this);
            mBaseDate.timezone = tz;
            mBaseDate.normalize(true);
            mCurrentTime.switchTimezone(tz);
            invalidate();
        }
    };

    // Sets the "clicked" color from the clicked event
    private final Runnable mSetClick = new Runnable() {
        @Override
        public void run() {
                mClickedEvent = mSavedClickedEvent;
                mSavedClickedEvent = null;
                AppointmentView.this.invalidate();
        }
    };

    // Clears the "clicked" color from the clicked event and launch the event
    private final Runnable mClearClick = new Runnable() {
        @Override
        public void run() {
            if (mClickedEvent != null) {
                if (mMessageHandler != null) {
                	EventMessage eventMsg = new EventMessage();
                	eventMsg.type = EventMessage.TYPE_VIEW;
                	eventMsg.eventId = mClickedEvent.id;
                	eventMsg.startMillis = mClickedEvent.startMillis;
                	eventMsg.endMillis = mClickedEvent.endMillis;
                	eventMsg.clerkName = mAllClerks[mSelectionClerk];
                	mMessageHandler.handleMessage(eventMsg);
                }
            }
            mClickedEvent = null;
            AppointmentView.this.invalidate();
        }
    };

    private ArrayList<Event> mEvents = new ArrayList<Event>();
    private StaticLayout[] mLayouts = null;
    private int mSelectionClerk;        
    private int mSelectionHour;

    // Pre-allocate these objects and re-use them
    private final Rect mRect = new Rect();
    private final RectF mRoundRect = new RectF();
    private final Rect mSelectionRect = new Rect();
    private final Paint mPaint = new Paint();
    private final Paint mEventTextPaint = new Paint();
    private final Paint mSelectionPaint = new Paint();
    private float[] mLines;

    private boolean mRemeasure = true;

    private final EventLoader mEventLoader;
    protected final EventGeometry mEventGeometry;

    private static float GRID_LINE_LEFT_MARGIN = 0;
    private static final float GRID_LINE_INNER_WIDTH = 1;

    private static final int CLERK_GAP = 1;
    private static final int HOUR_GAP = 1;

    private static int HOURS_TOP_MARGIN = 2;
    private static int HOURS_LEFT_MARGIN = 2;
    private static int HOURS_RIGHT_MARGIN = 4;
    private static int HOURS_MARGIN = HOURS_LEFT_MARGIN + HOURS_RIGHT_MARGIN;
    private static int NEW_EVENT_MARGIN = 4;
    private static int NEW_EVENT_WIDTH = 2;
    private static int NEW_EVENT_MAX_LENGTH = 16;

    private static int CURRENT_TIME_LINE_SIDE_BUFFER = 4;
    private static int CURRENT_TIME_LINE_TOP_OFFSET = 2;

    /* package */ static final int MINUTES_PER_HOUR = 60;
    /* package */ static final int MINUTES_PER_DAY = MINUTES_PER_HOUR * 24;
    /* package */ static final int MILLIS_PER_MINUTE = 60 * 1000;
    /* package */ static final int MILLIS_PER_HOUR = (3600 * 1000);
    /* package */ static final int MILLIS_PER_DAY = MILLIS_PER_HOUR * 24;

    private static int CLERK_HEADER_RIGHT_MARGIN = 4;
    private static int CLERK_HEADER_BOTTOM_MARGIN = 3;
    private static float CLERK_HEADER_FONT_SIZE = 20;
    private static float EVENT_TEXT_FONT_SIZE = 12;
    private static float HOURS_TEXT_SIZE = 12;
    private static int MIN_CELL_WIDTH_FOR_TEXT = 20;
    private static final int MAX_EVENT_TEXT_LEN = 500;
    // smallest height to draw an event with
    private static float MIN_EVENT_HEIGHT = 24.0F; // in pixels
    private static int EVENT_RECT_TOP_MARGIN = 1;
    private static int EVENT_RECT_BOTTOM_MARGIN = 0;
    private static int EVENT_RECT_LEFT_MARGIN = 1;
    private static int EVENT_RECT_RIGHT_MARGIN = 0;
    private static int EVENT_RECT_STROKE_WIDTH = 2;
    private static int EVENT_TEXT_TOP_MARGIN = 2;
    private static int EVENT_TEXT_BOTTOM_MARGIN = 2;
    private static int EVENT_TEXT_LEFT_MARGIN = 6;
    private static int EVENT_TEXT_RIGHT_MARGIN = 6;
    
    private static int mEventTextColor;
    private static int mCalendarClerkBannerTextColor;
    private static int mCalendarGridAreaSelected;
    private static int mCalendarGridLineInnerHorizontalColor;
    private static int mCalendarGridLineInnerVerticalColor;
    private static int mFutureBgColor;
    private static int mFutureBgColorRes;
    private static int mBgColor;
    private static int mNewEventHintColor;
    private static int mCalendarHourLabelColor;

    private int mMaxViewStartX;
    private int mMaxViewStartY;
    private int mViewHeight;
    private int mViewWidth;
    
    private int mCellWidth;
    private int mContentWidth;
    private int mContentHeight;
    
    private static int mCellHeight = 0; // shared among all DayViews

    private int mHoursTextHeight;

    /**
     * The height of the day names/numbers
     */
    private static int CLERK_HEADER_HEIGHT = 45;

    protected int mNumShownCols = 7;

    /** Width of the time line (list of hours) to the left. */
    private int mHoursWidth;
    private String[] mHourStrs;

    private final ArrayList<Event> mSelectedEvents = new ArrayList<Event>();
    private boolean mComputeSelectedEvents;
    private Event mSelectedEvent;
    protected final Resources mResources;
    protected final Drawable mCurrentTimeLine;

    /**
     * The initial state of the touch mode when we enter this view.
     */
    private static final int TOUCH_MODE_INITIAL_STATE = 0;

    /**
     * Indicates we just received the touch event and we are waiting to see if
     * it is a tap or a scroll gesture.
     */
    private static final int TOUCH_MODE_DOWN = 1;

    /**
     * Indicates the touch gesture is a vertical scroll
     */
    private static final int TOUCH_MODE_VSCROLL = 0x20;

    /**
     * Indicates the touch gesture is a horizontal scroll
     */
    private static final int TOUCH_MODE_HSCROLL = 0x40;

    private int mTouchMode = TOUCH_MODE_INITIAL_STATE;

    /**
     * The selection modes are HIDDEN, PRESSED, SELECTED, and LONGPRESS.
     */
    private static final int SELECTION_HIDDEN = 0;
    private static final int SELECTION_PRESSED = 1; // D-pad down but not up yet
    private static final int SELECTION_SELECTED = 2;
    private static final int SELECTION_LONGPRESS = 3;

    private int mSelectionMode = SELECTION_HIDDEN;

    private boolean mScrolling = false;

    private final GestureDetector mGestureDetector;
    private final OverScroller mScroller;
    private final EdgeEffect mEdgeEffectTop;
    private final EdgeEffect mEdgeEffectBottom;
    
    private String[] mAllClerks;
    private MessageHandler mMessageHandler;
    
    public interface MessageHandler {
    	public class EventMessage {
    		public static final int TYPE_VIEW = 1;
        	public static final int TYPE_NEW = 1 << 1;
    		
    		public long eventId;
    		public String clerkName;
    		public int type;
    		public long startMillis;
    		public long endMillis;
    	}
    	
    	void handleMessage(EventMessage eventMsg);
    }

    public AppointmentView(Context context, String[] allClerks, int numShownCols, EventLoader eventLoader, MessageHandler messageHandler) {
        super(context);
        mContext = context;

        mResources = context.getResources();
        mAllClerks = allClerks;
        mNumShownCols = numShownCols;
        mMessageHandler = messageHandler;

        CLERK_HEADER_BOTTOM_MARGIN = (int) mResources.getDimension(R.dimen.clerk_header_bottom_margin);
        HOURS_TEXT_SIZE = (int) mResources.getDimension(R.dimen.hours_text_size);
        HOURS_LEFT_MARGIN = (int) mResources.getDimension(R.dimen.hours_left_margin);
        HOURS_RIGHT_MARGIN = (int) mResources.getDimension(R.dimen.hours_right_margin);
        
        EVENT_TEXT_FONT_SIZE = (int) mResources.getDimension(R.dimen.appoint_list_view_event_text_size);
        MIN_EVENT_HEIGHT = mResources.getDimension(R.dimen.event_min_height);
        EVENT_TEXT_TOP_MARGIN = (int) mResources.getDimension(R.dimen.event_text_vertical_margin);
        EVENT_TEXT_BOTTOM_MARGIN = EVENT_TEXT_TOP_MARGIN;

        EVENT_TEXT_LEFT_MARGIN = (int) mResources
                .getDimension(R.dimen.event_text_horizontal_margin);
        EVENT_TEXT_RIGHT_MARGIN = EVENT_TEXT_LEFT_MARGIN;

        HOURS_MARGIN = HOURS_LEFT_MARGIN + HOURS_RIGHT_MARGIN;

        mCurrentTimeLine = mResources.getDrawable(R.drawable.timeline_indicator_holo_light);
        mNewEventHintColor =  mResources.getColor(R.color.new_event_hint_text_color);

        mEventLoader = eventLoader;
        mEventGeometry = new EventGeometry();
        mEventGeometry.setMinEventHeight(MIN_EVENT_HEIGHT);
        mEventGeometry.setHourGap(HOUR_GAP);
        mEventGeometry.setCellMargin(CLERK_GAP);
        mGestureDetector = new GestureDetector(context, new CalendarGestureListener());
        mCellHeight = DEFAULT_CELL_HEIGHT;
        
        mScroller = new OverScroller(context);
        mEdgeEffectTop = new EdgeEffect(context);
        mEdgeEffectBottom = new EdgeEffect(context);
        mOnDownDelay = ViewConfiguration.getTapTimeout();

        init(context);
    }

    @Override
    protected void onAttachedToWindow() {
        if (mHandler == null) {
            mHandler = getHandler();
            mHandler.post(mUpdateCurrentTime);
        }
    }

    private void init(Context context) {
        setFocusable(true);

        // Allow focus in touch mode so that we can do keyboard shortcuts
        // even after we've entered touch mode.
        setFocusableInTouchMode(true);
        setClickable(true);

        mCurrentTime = new Time(CalendarUtils.getTimeZone(context, mTZUpdater));
        long currentTime = System.currentTimeMillis();
        mCurrentTime.set(currentTime);
        mTodayJulianDay = Time.getJulianDay(currentTime, mCurrentTime.gmtoff);
        mCalendarClerkBannerTextColor = mResources.getColor(R.color.calendar_clerk_banner_text_color);
        mFutureBgColorRes = mResources.getColor(R.color.calendar_future_bg_color);
        mBgColor = mResources.getColor(R.color.calendar_hour_background);
        mCalendarGridAreaSelected = mResources.getColor(R.color.calendar_grid_area_selected);
        mCalendarGridLineInnerHorizontalColor = mResources
                .getColor(R.color.calendar_grid_line_inner_horizontal_color);
        mCalendarGridLineInnerVerticalColor = mResources
                .getColor(R.color.calendar_grid_line_inner_vertical_color);
        mCalendarHourLabelColor = mResources.getColor(R.color.calendar_hour_label);
        mEventTextColor = mResources.getColor(R.color.calendar_event_text_color);

        mEventTextPaint.setTextSize(EVENT_TEXT_FONT_SIZE);
        mEventTextPaint.setTextAlign(Paint.Align.LEFT);
        mEventTextPaint.setAntiAlias(true);

        int gridLineColor = mResources.getColor(R.color.calendar_grid_line_highlight_color);
        Paint p = mSelectionPaint;
        p.setColor(gridLineColor);
        p.setStyle(Style.FILL);
        p.setAntiAlias(false);

        p = mPaint;
        p.setAntiAlias(true);
        p.setTextSize(HOURS_TEXT_SIZE);
        p.setTypeface(null);
        handleOnResume();
        
        p.setTextSize(HOURS_TEXT_SIZE);
        mHoursWidth = HOURS_MARGIN + computeMaxStringWidth(mHoursWidth, mHourStrs, p);

        mBaseDate = new Time(CalendarUtils.getTimeZone(context, mTZUpdater));
        long millis = System.currentTimeMillis();
        mBaseDate.set(millis);

        // mLines is the array of points used with Canvas.drawLines() in
        // drawGridBackground() and drawAllDayEvents().  Its size depends
        // on the max number of lines that can ever be drawn by any single
        // drawLines() call in either of those methods.
        final int maxGridLines = (24 + 1)  // max horizontal lines we might draw
                + (mAllClerks.length + 1); // max vertical lines we might draw
        mLines = new float[maxGridLines * 4];
    }

    public void handleOnResume() {
        mFutureBgColor = mFutureBgColorRes;
        mHourStrs = s24Hours;
        mSelectionMode = SELECTION_HIDDEN;
    }

    /**
     * Returns the start of the selected time in milliseconds since the epoch.
     *
     * @return selected time in UTC milliseconds since the epoch.
     */
    long getSelectedTimeInMillis() {
        Time time = new Time(mBaseDate);
        time.setJulianDay(mCurrentSelectedJulianDay);
        time.hour = mSelectionHour;

        // We ignore the "isDst" field because we want normalize() to figure
        // out the correct DST value and not adjust the selected time based
        // on the current setting of DST.
        return time.normalize(true /* ignore isDst */);
    }

    /**
     * Returns the start of the selected time in minutes since midnight,
     * local time.  The derived class must ensure that this is consistent
     * with the return value from getSelectedTimeInMillis().
     */
    int getSelectedMinutesSinceMidnight() {
        return mSelectionHour * MINUTES_PER_HOUR;
    }
    
    int getClerkIndexByName(String name) {
    	int index = 0;
    	for (String temp : mAllClerks) {
    		if (temp.equalsIgnoreCase(name)) {
    			break;
    		}
    		index++;
    	}
    	
    	return index;
    }

    public void setSelected(Time time, String loginClerk, boolean ignoreTime, boolean animateToday) {
        mBaseDate.set(time);
        setSelectedHour(mBaseDate.hour);
        setSelectedEvent(null);
        long millis = mBaseDate.toMillis(false /* use isDst */);
        setSelectedDay(Time.getJulianDay(millis, mBaseDate.gmtoff));
        setSelectedClerk(getClerkIndexByName(loginClerk));
        mSelectedEvents.clear();
        mComputeSelectedEvents = true;

        mRemeasure = true;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        mViewWidth = width;
        mViewHeight = height;
        
        int gridAreaWidth = width - mHoursWidth;
        mCellWidth = (gridAreaWidth - (mNumShownCols * CLERK_GAP)) / mNumShownCols;

        Paint p = new Paint();
        p.setTextSize(HOURS_TEXT_SIZE);
        mHoursTextHeight = (int) Math.abs(p.ascent());
        remeasure(width, height);
    }

    /**
     * Measures the space needed for various parts of the view after
     * loading new events.  This can change if there are all-day events.
     */
    private void remeasure(int width, int height) {
        mContentWidth = CLERK_GAP + mAllClerks.length * (mCellWidth + CLERK_GAP) + mHoursWidth;
        mContentHeight = HOUR_GAP + 24 * (mCellHeight + HOUR_GAP) + CLERK_HEADER_HEIGHT;
        
        mEventGeometry.setHourHeight(mCellHeight);
        mMaxViewStartX = mContentWidth - width;
        // Compute the top of our reachable view
        mMaxViewStartY = mContentHeight - height;
        
        mEdgeEffectTop.setSize(mContentWidth, width);
        mEdgeEffectBottom.setSize(mContentWidth, height);
    }

    public void reloadEvents() {
        if (mContext == null) {
            return;
        }

        // Make sure our time zones are up to date
        mTZUpdater.run();

        setSelectedEvent(null);
        mSelectedEvents.clear();

        // load events in the background
        final ArrayList<Event> events = new ArrayList<Event>();
        mEventLoader.loadEventsInBackground(1, events, mCurrentSelectedJulianDay, new Runnable() {

            public void run() {
                mEvents = events;
                // New events, new layouts
                if (mLayouts == null || mLayouts.length < events.size()) {
                    mLayouts = new StaticLayout[events.size()];
                } else {
                    Arrays.fill(mLayouts, null);
                }

                mRemeasure = true;
                mComputeSelectedEvents = true;
                invalidate();
            }
        }, null);
    }

    public void setEventsAlpha(int alpha) {
        mEventsAlpha = alpha;
        invalidate();
    }

    public int getEventsAlpha() {
        return mEventsAlpha;
    }

    public void stopEventsAnimation() {
        mEventsAlpha = 255;
    }

    @SuppressLint("WrongCall")
	@Override
    protected void onDraw(Canvas canvas) {
        if (mRemeasure) {
            remeasure(getWidth(), getHeight());
            mRemeasure = false;
        }
        canvas.save();

        // offset canvas by the current drag and header position
        canvas.translate(0, CLERK_HEADER_HEIGHT);
        canvas.save();
        doDraw(canvas);
        canvas.restore();
        canvas.translate(0, -CLERK_HEADER_HEIGHT);

        // Draw the fixed areas (that don't scroll) directly to the canvas.
        drawAfterScroll(canvas);
        mComputeSelectedEvents = false;

        // Draw overscroll glow
        if (!mEdgeEffectTop.isFinished()) {
            if (CLERK_HEADER_HEIGHT != 0) {
                canvas.translate(0, CLERK_HEADER_HEIGHT);
            }
            if (mEdgeEffectTop.draw(canvas)) {
                invalidate();
            }
            if (CLERK_HEADER_HEIGHT != 0) {
                canvas.translate(0, -CLERK_HEADER_HEIGHT);
            }
        }
        if (!mEdgeEffectBottom.isFinished()) {
        	canvas.translate(0, 0);
            canvas.rotate(180);
            canvas.translate(-mContentWidth, -mContentHeight);
            if (mEdgeEffectBottom.draw(canvas)) {
                invalidate();
            }
        }
        canvas.restore();
    }

    private void drawAfterScroll(Canvas canvas) {
        Paint p = mPaint;
        Rect r = mRect;

        drawAllClerkHighlights(r, canvas, p);
        drawScrollLine(r, canvas, p);
        drawClerkHeaderLoop(r, canvas, p);
    }

    private void drawScrollLine(Rect r, Canvas canvas, Paint p) {
        final int right = computeClerkLeftPosition(mAllClerks.length);
        final int y = CLERK_HEADER_HEIGHT - 1;

        p.setAntiAlias(false);
        p.setStyle(Style.FILL);

        p.setColor(mCalendarGridLineInnerHorizontalColor);
        p.setStrokeWidth(GRID_LINE_INNER_WIDTH);
        canvas.drawLine(GRID_LINE_LEFT_MARGIN, y, right, y, p);
        p.setAntiAlias(true);
    }

    // Computes the x position for the left side of the given clerk index (base 0)
    private int computeClerkLeftPosition(int index) {
        int effectiveWidth = mViewWidth - mHoursWidth;
        return index * effectiveWidth / mNumShownCols + mHoursWidth;
    }

    private void drawAllClerkHighlights(Rect r, Canvas canvas, Paint p) {
        if (mFutureBgColor != 0) {
            // First, color the labels area light gray
            r.top = 0;
            r.bottom = CLERK_HEADER_HEIGHT;
            r.left = 0;
            r.right = mContentWidth;
            p.setColor(mBgColor);
            p.setStyle(Style.FILL);
            canvas.drawRect(r, p);
        }
    }

    private void drawClerkHeaderLoop(Rect r, Canvas canvas, Paint p) {
        p.setTypeface(mBold);
        p.setTextAlign(Paint.Align.CENTER);
        int cell = 0;
        String[] clerkNames = mAllClerks;

        p.setAntiAlias(true);
        for (int index = 0; index < clerkNames.length; index++, cell++) {
            int color = mCalendarClerkBannerTextColor;
            p.setColor(color);
            drawClerkNameHeader(clerkNames[index], index, cell, canvas, p);
        }
        p.setTypeface(null);
    }

    private void drawCurrentTimeLine(final int top, Canvas canvas,
            Paint p) {
    	Rect r = mRect;
        r.left = mHoursWidth - CURRENT_TIME_LINE_SIDE_BUFFER - 1;
        r.right = mContentWidth + CURRENT_TIME_LINE_SIDE_BUFFER + 1;

        r.top = top - CURRENT_TIME_LINE_TOP_OFFSET;
        r.bottom = r.top + mCurrentTimeLine.getIntrinsicHeight();

        mCurrentTimeLine.setBounds(r);
        mCurrentTimeLine.draw(canvas);
    }

    private void doDraw(Canvas canvas) {
        Paint p = mPaint;
        Rect r = mRect;

        drawBgColors(r, canvas, p);
        drawGrids(r, canvas, p);
        drawHours(r, canvas, p);

        p.setAntiAlias(false);
        int alpha = p.getAlpha();
        p.setAlpha(mEventsAlpha);
        for (int index = 0; index < mAllClerks.length; index++) {
            drawEvents(mCurrentSelectedJulianDay, index, HOUR_GAP, canvas, p);
        }
        
        if (mCurrentSelectedJulianDay == mTodayJulianDay) {
            int lineY = mCurrentTime.hour * (mCellHeight + HOUR_GAP)
                    + ((mCurrentTime.minute * mCellHeight) / 60) + 1;

            // And the current time shows up somewhere on the screen
            drawCurrentTimeLine(lineY, canvas, p);
        }
        
        p.setAntiAlias(true);
        p.setAlpha(alpha);

        drawSelectedRect(r, canvas, p);
    }

    private void drawSelectedRect(Rect r, Canvas canvas, Paint p) {
        // Draw a highlight on the selected hour (if needed)
        if (mSelectionMode != SELECTION_HIDDEN) {
            int clerkIndex = mSelectionClerk;
            r.top = mSelectionHour * (mCellHeight + HOUR_GAP);
            r.bottom = r.top + mCellHeight + HOUR_GAP;
            r.left = computeClerkLeftPosition(clerkIndex) + 1;
            r.right = computeClerkLeftPosition(clerkIndex + 1) + 1;

            // Draw the highlight on the grid
            p.setColor(mCalendarGridAreaSelected);
            r.top += HOUR_GAP;
            r.right -= CLERK_GAP;
            p.setAntiAlias(false);
            canvas.drawRect(r, p);

            // Draw a "new event hint" on top of the highlight
            // For the week view, show a "+", for day view, show "+ New event"
            p.setColor(mNewEventHintColor);
            p.setStrokeWidth(NEW_EVENT_WIDTH);
            int width = r.right - r.left;
            int midX = r.left + width / 2;
            int midY = r.top + mCellHeight / 2;
            int length = Math.min(mCellHeight, width) - NEW_EVENT_MARGIN * 2;
            length = Math.min(length, NEW_EVENT_MAX_LENGTH);
            int verticalPadding = (mCellHeight - length) / 2;
            int horizontalPadding = (width - length) / 2;
            canvas.drawLine(r.left + horizontalPadding, midY, r.right - horizontalPadding,
                    midY, p);
            canvas.drawLine(midX, r.top + verticalPadding, midX, r.bottom - verticalPadding, p);
        }
    }

    private void drawHours(Rect r, Canvas canvas, Paint p) {
    	setupHourTextPaint(p);
        int y = HOUR_GAP + mHoursTextHeight + HOURS_TOP_MARGIN;

        for (int i = 0; i < 24; i++) {
            String time = mHourStrs[i];
            canvas.drawText(time, mHoursWidth / 2, y, p);
            y += mCellHeight + HOUR_GAP;
        }
    }

    private void setupHourTextPaint(Paint p) {
        p.setColor(mCalendarHourLabelColor);
        p.setTextSize(HOURS_TEXT_SIZE);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextAlign(Paint.Align.CENTER);
        p.setAntiAlias(true);
    }

    private void drawClerkNameHeader(String clearkName, int clerkIndex, int cell, Canvas canvas, Paint p) {
        int x;
        float y = CLERK_HEADER_HEIGHT - CLERK_HEADER_BOTTOM_MARGIN;

        // Draw day of the month
        x = computeClerkLeftPosition(clerkIndex) - CLERK_HEADER_RIGHT_MARGIN;
        x += mCellWidth / 2;
        p.setTextAlign(Align.CENTER);
        p.setTextSize(CLERK_HEADER_FONT_SIZE);

        canvas.drawText(clearkName, x, y, p);
    }

    private void drawGrids(Rect r, Canvas canvas, Paint p) {
        Paint.Style savedStyle = p.getStyle();

        final float stopX = computeClerkLeftPosition(mAllClerks.length);
        float y = 0;
        final float deltaY = mCellHeight + HOUR_GAP;
        int linesIndex = 0;
        final float startY = 0;
        final float stopY = mContentHeight;
        float x = mHoursWidth;

        // Draw the inner horizontal grid lines
        p.setColor(mCalendarGridLineInnerHorizontalColor);
        p.setStrokeWidth(GRID_LINE_INNER_WIDTH);
        p.setAntiAlias(false);
        y = 0;
        linesIndex = 0;
        for (int hour = 0; hour <= 24; hour++) {
            mLines[linesIndex++] = GRID_LINE_LEFT_MARGIN;
            mLines[linesIndex++] = y;
            mLines[linesIndex++] = stopX;
            mLines[linesIndex++] = y;
            y += deltaY;
        }
        if (mCalendarGridLineInnerVerticalColor != mCalendarGridLineInnerHorizontalColor) {
            canvas.drawLines(mLines, 0, linesIndex, p);
            linesIndex = 0;
            p.setColor(mCalendarGridLineInnerVerticalColor);
        }

        // Draw the inner vertical grid lines
        for (int index = 0; index <= mAllClerks.length; index++) {
            x = computeClerkLeftPosition(index);
            mLines[linesIndex++] = x;
            mLines[linesIndex++] = startY;
            mLines[linesIndex++] = x;
            mLines[linesIndex++] = stopY;
        }
        canvas.drawLines(mLines, 0, linesIndex, p);

        // Restore the saved style.
        p.setStyle(savedStyle);
        p.setAntiAlias(true);
    }

    /**
     * @param r
     * @param canvas
     * @param p
     */
    private void drawBgColors(Rect r, Canvas canvas, Paint p) {
        // Draw the hours background color
        r.top = 0;
        r.bottom = mContentHeight;
        r.left = 0;
        r.right = mHoursWidth;
        p.setColor(mBgColor);
        p.setStyle(Style.FILL);
        p.setAntiAlias(false);
        canvas.drawRect(r, p);

        // Draw a white background for the time later than current time
        if (mCurrentSelectedJulianDay >= mTodayJulianDay) {
        	int lineY = 0;
        	if (mCurrentSelectedJulianDay == mTodayJulianDay) {
        		lineY = mCurrentTime.hour * (mCellHeight + HOUR_GAP)
                        + ((mCurrentTime.minute * mCellHeight) / 60) + 1;
        	}

            r.left = mHoursWidth;
            r.right = mContentWidth;
            r.top = lineY;
            r.bottom = mContentHeight;
            p.setColor(mFutureBgColor);
            canvas.drawRect(r, p);
        }

        p.setAntiAlias(true);
    }

    Event getSelectedEvent() {
        if (mSelectedEvent == null) {
            // There is no event at the selected hour, so create a new event.
            return getNewEvent(mSelectionClerk, getSelectedTimeInMillis(),
                    getSelectedMinutesSinceMidnight());
        }
        return mSelectedEvent;
    }

    boolean isEventSelected() {
        return (mSelectedEvent != null);
    }

    Event getNewEvent() {
        return getNewEvent(mSelectionClerk, getSelectedTimeInMillis(),
                getSelectedMinutesSinceMidnight());
    }

    static Event getNewEvent(int julianDay, long utcMillis,
            int minutesSinceMidnight) {
        Event event = Event.newInstance();
        event.startDay = julianDay;
        event.endDay = julianDay;
        event.startMillis = utcMillis;
        event.endMillis = event.startMillis + MILLIS_PER_HOUR;
        event.startTime = minutesSinceMidnight;
        event.endTime = event.startTime + MINUTES_PER_HOUR;
        return event;
    }

    private int computeMaxStringWidth(int currentMax, String[] strings, Paint p) {
        float maxWidthF = 0.0f;

        int len = strings.length;
        for (int i = 0; i < len; i++) {
            float width = p.measureText(strings[i]);
            maxWidthF = Math.max(width, maxWidthF);
        }
        int maxWidth = (int) (maxWidthF + 0.5);
        if (maxWidth < currentMax) {
            maxWidth = currentMax;
        }
        return maxWidth;
    }

    private void setupTextRect(Rect r) {
        if (r.bottom <= r.top || r.right <= r.left) {
            r.bottom = r.top;
            r.right = r.left;
            return;
        }

        if (r.bottom - r.top > EVENT_TEXT_TOP_MARGIN + EVENT_TEXT_BOTTOM_MARGIN) {
            r.top += EVENT_TEXT_TOP_MARGIN;
            r.bottom -= EVENT_TEXT_BOTTOM_MARGIN;
        }
        if (r.right - r.left > EVENT_TEXT_LEFT_MARGIN + EVENT_TEXT_RIGHT_MARGIN) {
            r.left += EVENT_TEXT_LEFT_MARGIN;
            r.right -= EVENT_TEXT_RIGHT_MARGIN;
        }
    }


    /**
     * Return the layout for a numbered event. Create it if not already existing
     */
    private StaticLayout getEventLayout(StaticLayout[] layouts, int i, Event event, Paint paint,
            Rect r) {
        if (i < 0 || i >= layouts.length) {
            return null;
        }

        StaticLayout layout = layouts[i];
        // Check if we have already initialized the StaticLayout and that
        // the width hasn't changed (due to vertical resizing which causes
        // re-layout of events at min height)
        if (layout == null || r.width() != layout.getWidth()) {
            SpannableStringBuilder bob = new SpannableStringBuilder();
            if (event.title != null) {
                // MAX - 1 since we add a space
                bob.append(drawTextSanitizer(event.title.toString(), MAX_EVENT_TEXT_LEN - 1));
                bob.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, bob.length(), 0);
                bob.append(' ');
            }
            if (event.location != null) {
                bob.append(drawTextSanitizer(event.location.toString(),
                        MAX_EVENT_TEXT_LEN - bob.length()));
            }

            paint.setColor(mEventTextColor);

            // Leave a one pixel boundary on the left and right of the rectangle for the event
            layout = new StaticLayout(bob, 0, bob.length(), new TextPaint(paint), r.width(),
                    Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true, null, r.width());

            layouts[i] = layout;
        }
        layout.getPaint().setAlpha(mEventsAlpha);
        return layout;
    }

    private void drawEvents(int day, int clerkIndex, int top, Canvas canvas, Paint p) {
    	String clerkName = mAllClerks[clerkIndex];
    	
        Paint eventTextPaint = mEventTextPaint;
        int left = computeClerkLeftPosition(clerkIndex) + 1;
        int cellWidth = mCellWidth - 2;
        int cellHeight = mCellHeight;

        // Use the selected hour as the selection region
        Rect selectionArea = mSelectionRect;
        selectionArea.top = top + mSelectionHour * (cellHeight + HOUR_GAP);
        selectionArea.bottom = selectionArea.top + cellHeight;
        selectionArea.left = left;
        selectionArea.right = selectionArea.left + cellWidth;

        final ArrayList<Event> events = mEvents;
        int numEvents = events.size();
        EventGeometry geometry = mEventGeometry;

        int alpha = eventTextPaint.getAlpha();
        eventTextPaint.setAlpha(mEventsAlpha);
        for (int i = 0; i < numEvents; i++) {
            Event event = events.get(i);
            if (clerkName != null && !clerkName.equalsIgnoreCase(event.title.toString())) {
            	continue;
            }
            
            if (!geometry.computeEventRect(day, left, top, cellWidth, event)) {
                continue;
            }

            if (day == mSelectionClerk && mComputeSelectedEvents
                    && geometry.eventIntersectsSelection(event, selectionArea)) {
                mSelectedEvents.add(event);
            }

            Rect r = drawEventRect(event, canvas, p, eventTextPaint);
            setupTextRect(r);

            StaticLayout layout = getEventLayout(mLayouts, i, event, eventTextPaint, r);
            drawEventText(layout, r, canvas, false);
        }
        eventTextPaint.setAlpha(alpha);
    }

    private Rect drawEventRect(Event event, Canvas canvas, Paint p, Paint eventTextPaint) {
        // Draw the Event Rect
        RectF r = mRoundRect;
        r.top = event.top + EVENT_RECT_TOP_MARGIN; 
        r.bottom = event.bottom - EVENT_RECT_BOTTOM_MARGIN;
        r.left = event.left + EVENT_RECT_LEFT_MARGIN;
        r.right = event.right;

        p.setAntiAlias(true);  
        p.setStrokeWidth(EVENT_RECT_STROKE_WIDTH);
        p.setColor(event.color);
        p.setAlpha(mEventsAlpha);
        canvas.drawRoundRect(r, 5, 5, p);

        // Setup rect for drawEventText which follows
        Rect textRect = mRect;
        textRect.top = (int) event.top + EVENT_RECT_TOP_MARGIN;
        textRect.bottom = (int) event.bottom - EVENT_RECT_BOTTOM_MARGIN;
        textRect.left = (int) event.left + EVENT_RECT_LEFT_MARGIN;
        textRect.right = (int) event.right - EVENT_RECT_RIGHT_MARGIN;
        return textRect;
    }

    private final Pattern drawTextSanitizerFilter = Pattern.compile("[\t\n],");

    // Sanitize a string before passing it to drawText or else we get little
    // squares. For newlines and tabs before a comma, delete the character.
    // Otherwise, just replace them with a space.
    private String drawTextSanitizer(String string, int maxEventTextLen) {
        Matcher m = drawTextSanitizerFilter.matcher(string);
        string = m.replaceAll(",");

        int len = string.length();
        if (maxEventTextLen <= 0) {
            string = "";
            len = 0;
        } else if (len > maxEventTextLen) {
            string = string.substring(0, maxEventTextLen);
            len = maxEventTextLen;
        }

        return string.replace('\n', ' ');
    }

    private void drawEventText(StaticLayout eventLayout, Rect rect, Canvas canvas, boolean center) {
        int width = rect.right - rect.left;
        int height = rect.bottom - rect.top;

        // If the rectangle is too small for text, then return
        if (eventLayout == null || width < MIN_CELL_WIDTH_FOR_TEXT) {
            return;
        }

        int totalLineHeight = 0;
        int lineCount = eventLayout.getLineCount();
        for (int i = 0; i < lineCount; i++) {
            int lineBottom = eventLayout.getLineBottom(i);
            if (lineBottom <= height) {
                totalLineHeight = lineBottom;
            } else {
                break;
            }
        }

        // Use a StaticLayout to format the string.
        canvas.save();
      //  canvas.translate(rect.left, rect.top + (rect.bottom - rect.top / 2));
        int padding = center? (rect.bottom - rect.top - totalLineHeight) / 2 : 0;
        canvas.translate(rect.left, rect.top + padding);
        rect.left = 0;
        rect.right = width;
        rect.top = 0;
        rect.bottom = totalLineHeight;

        // There's a bug somewhere. If this rect is outside of a previous
        // cliprect, this becomes a no-op. What happens is that the text draw
        // past the event rect. The current fix is to not draw the staticLayout
        // at all if it is completely out of bound.
        canvas.clipRect(rect);
        eventLayout.draw(canvas);
        canvas.restore();
    }

    // The following routines are called from the parent activity when certain
    // touch events occur.
    private void doDown(MotionEvent ev) {
    	if (!mScroller.isFinished()) {
    		mScroller.forceFinished(true);
    	}
    	
        mTouchMode = TOUCH_MODE_DOWN;
        int x = (int) ev.getX();
        int y = (int) ev.getY();

        // Save selection information: we use setSelectionFromPosition to find the selected event
        // in order to show the "clicked" color. But since it is also setting the selected info
        // for new events, we need to restore the old info after calling the function.
        Event oldSelectedEvent = mSelectedEvent;
        int oldSelectionClerk = mSelectionClerk;
        int oldSelectionHour = mSelectionHour;
        if (setSelectionFromPosition(x, y, false)) {
            // If a time was selected (a blue selection box is visible) and the click location
            // is in the selected time, do not show a click on an event to prevent a situation
            // of both a selection and an event are clicked when they overlap.
            boolean pressedSelected = (mSelectionMode != SELECTION_HIDDEN)
                    && oldSelectionClerk == mSelectionClerk && oldSelectionHour == mSelectionHour;
            if (!pressedSelected && mSelectedEvent != null) {
                mSavedClickedEvent = mSelectedEvent;
                mDownTouchTime = System.currentTimeMillis();
                postDelayed (mSetClick,mOnDownDelay);
            } else {
                eventClickCleanup();
            }
        }
        mSelectedEvent = oldSelectedEvent;
        mSelectionClerk = oldSelectionClerk;
        mSelectionHour = oldSelectionHour;
        invalidate();
    }

    private void doSingleTapUp(MotionEvent ev) {
        if (mScrolling) {
            return;
        }

        int x = (int) ev.getX();
        int y = (int) ev.getY();
        int selectedClerk = mSelectionClerk;
        int selectedHour = mSelectionHour;

        boolean validPosition = setSelectionFromPosition(x, y, false);
        if (!validPosition) {
            return;
        }

        boolean pressedSelected = mSelectionMode != SELECTION_HIDDEN
                && selectedClerk == mSelectionClerk && selectedHour == mSelectionHour;

        if (pressedSelected && mSavedClickedEvent == null) {
            // If the tap is on an already selected hour slot, then create a new
            // event
            mSelectionMode = SELECTION_SELECTED;
            long startMillis = getSelectedTimeInMillis();
            
            Time endTime = new Time();
            endTime.set(startMillis);
            endTime.hour++;
            
            long endMillis = endTime.toMillis(false);
            
            if (mMessageHandler != null) {
            	EventMessage eventMsg = new EventMessage();
            	eventMsg.type = EventMessage.TYPE_NEW;
            	eventMsg.eventId = -1;
            	eventMsg.startMillis = startMillis;
            	eventMsg.endMillis = endMillis;
            	eventMsg.clerkName = mAllClerks[mSelectionClerk];
            	mMessageHandler.handleMessage(eventMsg);
            }
        } else if (mSelectedEvent != null) {
            // If the tap is on an event, launch the "View event" view
            mSelectionMode = SELECTION_HIDDEN;
            long clearDelay = (CLICK_DISPLAY_DURATION + mOnDownDelay) -
                    (System.currentTimeMillis() - mDownTouchTime);
            if (clearDelay > 0) {
                this.postDelayed(mClearClick, clearDelay);
            } else {
                this.post(mClearClick);
            }
        } else {
            // Select time
            Time startTime = new Time(mBaseDate);
            startTime.setJulianDay(mCurrentSelectedJulianDay);
            startTime.hour = mSelectionHour;
            startTime.normalize(true /* ignore isDst */);

            Time endTime = new Time(startTime);
            endTime.hour++;

            mSelectionMode = SELECTION_SELECTED;
        }
        invalidate();
    }

    private void doLongPress(MotionEvent ev) {
        eventClickCleanup();
        if (mScrolling) {
            return;
        }

        int x = (int) ev.getX();
        int y = (int) ev.getY();

        boolean validPosition = setSelectionFromPosition(x, y, false);
        if (!validPosition) {
            // return if the touch wasn't on an area of concern
            return;
        }

        mSelectionMode = SELECTION_LONGPRESS;
        invalidate();
        performLongClick();
    }

    private void doScroll(MotionEvent e1, MotionEvent e2, float deltaX, float deltaY) {
        boolean isExceedEge = false;

        if (mTouchMode == TOUCH_MODE_DOWN) {
            int absDistanceX = Math.abs((int)deltaX);
            int absDistanceY = Math.abs((int)deltaY);
            Log.d("KAROS", deltaX + " " + deltaY);
            if (absDistanceX > absDistanceY) {
                mTouchMode = TOUCH_MODE_HSCROLL;
            } else {
                mTouchMode = TOUCH_MODE_VSCROLL;
            }
        } 
        	
        if ((mTouchMode & TOUCH_MODE_HSCROLL) != 0) {
        	final int pulledToX = (int) (this.getScrollX() + deltaX);
            if (pulledToX < 0) {
                isExceedEge = true;
            } else if (pulledToX > mMaxViewStartX) {
                isExceedEge = true;
            }
            
            if (!isExceedEge)
            	this.scrollBy((int)deltaX, 0);
        }

        if ((mTouchMode & TOUCH_MODE_VSCROLL) != 0) {
            // If dragging while already at the end, do a glow
            final int pulledToY = (int) (this.getScrollY() + deltaY);
            if (pulledToY < 0) {
                mEdgeEffectTop.onPull(deltaY / mViewHeight);
                if (!mEdgeEffectBottom.isFinished()) {
                    mEdgeEffectBottom.onRelease();
                }
                isExceedEge = true;
            } else if (pulledToY > mMaxViewStartY) {
                mEdgeEffectBottom.onPull(deltaY / mViewHeight);
                if (!mEdgeEffectTop.isFinished()) {
                    mEdgeEffectTop.onRelease();
                }
                
                isExceedEge = true;
            }
            
            if (!isExceedEge)
            	this.scrollBy(0, (int)deltaY);
        }

        mScrolling = true;
        mSelectionMode = SELECTION_HIDDEN;
        invalidate();
    }
    
    private void doFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    	mScrolling = true;
    	mScroller.fling(this.getScrollX(), this.getScrollY(), (int)-velocityX, (int)-velocityY, 0, mMaxViewStartX, 0, mMaxViewStartY, 0, 0);    	

        mTouchMode = TOUCH_MODE_INITIAL_STATE;
        invalidate();
    }
    
    @Override
    public void computeScroll() {
    	super.computeScroll();
    	
    	if (mScroller.computeScrollOffset()) {
    		int oldX = this.getScrollX();
    		int oldY = this.getScrollY();
    		
    		int currX = mScroller.getCurrX();
    		int currY = mScroller.getCurrY();
    		
    		int deltaX = currX - oldX;
    		int deltaY = currY - oldY;
    		
    		this.scrollBy(deltaX, deltaY);
    		
            invalidate();
    	}   
    }

    @SuppressLint("ClickableViewAccessibility")
	@Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (DEBUG) Log.e(TAG, "" + action + " ev.getPointerCount() = " + ev.getPointerCount());

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (DEBUG) {
                    Log.e(TAG, "ACTION_DOWN ev.getDownTime = " + ev.getDownTime() + " Cnt="
                            + ev.getPointerCount());
                }

                mGestureDetector.onTouchEvent(ev);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (DEBUG) Log.e(TAG, "ACTION_MOVE Cnt=" + ev.getPointerCount() + AppointmentView.this);
                mGestureDetector.onTouchEvent(ev);
                return true;

            case MotionEvent.ACTION_UP:
                if (DEBUG) Log.e(TAG, "ACTION_UP Cnt=" + ev.getPointerCount());
                mEdgeEffectTop.onRelease();
                mEdgeEffectBottom.onRelease();
                mGestureDetector.onTouchEvent(ev);

                // If we were scrolling, then reset the selected hour so that it
                // is visible.
                if (mScrolling) {
                    mScrolling = false;
                }

                return true;

                // This case isn't expected to happen.
            case MotionEvent.ACTION_CANCEL:
                if (DEBUG) Log.e(TAG, "ACTION_CANCEL");
                mGestureDetector.onTouchEvent(ev);
                mScrolling = false;
                return true;

            default:
                if (DEBUG) Log.e(TAG, "Not MotionEvent " + ev.toString());
                if (mGestureDetector.onTouchEvent(ev)) {
                    return true;
                }
                return super.onTouchEvent(ev);
        }
    }

    /**
     * Sets mSelectionClerk and mSelectionHour based on the (x,y) touch position.
     * If the touch position is not within the displayed grid, then this
     * method returns false.
     *
     * @param x the x position of the touch
     * @param y the y position of the touch
     * @param keepOldSelection - do not change the selection info (used for invoking accessibility
     *                           messages)
     * @return true if the touch position is valid
     */
    private boolean setSelectionFromPosition(int x, final int y, boolean keepOldSelection) {
    	int xInContent = getScrollX() + x;
    	int yInContent = getScrollY() + y;
    	
        Event savedEvent = null;
        int savedClerk = 0;
        int savedHour = 0;
        if (keepOldSelection) {
            // Store selection info and restore it at the end. This way, we can invoke the
            // right accessibility message without affecting the selection.
            savedEvent = mSelectedEvent;
            savedClerk = mSelectionClerk;
            savedHour = mSelectionHour;
        }
        if (xInContent < mHoursWidth) {
        	xInContent = mHoursWidth;
        }

        int clerkIndex = (xInContent - mHoursWidth) / (mCellWidth + CLERK_GAP);
        setSelectedClerk(clerkIndex);

        if (yInContent < CLERK_HEADER_HEIGHT) {
            return false;
        }
        
        int hour = (yInContent - CLERK_HEADER_HEIGHT) / (mCellHeight + HOUR_GAP);
        setSelectedHour(hour); 

        findSelectedEvent(xInContent, yInContent);

        // Restore old values
        if (keepOldSelection) {
            mSelectedEvent = savedEvent;
            mSelectionClerk = savedClerk;
            mSelectionHour = savedHour;
        }
        return true;
    }

    private void findSelectedEvent(int x, int y) {
    	int selectedDay = mCurrentSelectedJulianDay;
        int cellWidth = mCellWidth;
        ArrayList<Event> events = mEvents;
        int numEvents = events.size();
        int top = 0;
        setSelectedEvent(null);

        mSelectedEvents.clear();

        // Adjust y for the scrollable bitmap
        y += - CLERK_HEADER_HEIGHT;

        // Use a region around (x,y) for the selection region
        Rect region = mRect;
        region.left = x - 10;
        region.right = x + 10;
        region.top = y - 10;
        region.bottom = y + 10;

        EventGeometry geometry = mEventGeometry;

        for (int i = 0; i < numEvents; i++) {
            Event event = events.get(i);
            int clerkIndex = getClerkIndexByName(event.title.toString());
            int left = computeClerkLeftPosition(clerkIndex);
            // Compute the event rectangle.
            if (!geometry.computeEventRect(selectedDay, left, top, cellWidth, event)) {
                continue;
            }

            // If the event intersects the selection region, then add it to
            // mSelectedEvents.
            if (geometry.eventIntersectsSelection(event, region)) {
                mSelectedEvents.add(event);
            }
        }

        // If there are any events in the selected region, then assign the
        // closest one to mSelectedEvent.
        if (mSelectedEvents.size() > 0) {
            int len = mSelectedEvents.size();
            Event closestEvent = null;
            float minDist = mViewWidth + mViewHeight; // some large distance
            for (int index = 0; index < len; index++) {
                Event ev = mSelectedEvents.get(index);
                float dist = geometry.pointToEvent(x, y, ev);
                if (dist < minDist) {
                    minDist = dist;
                    closestEvent = ev;
                }
            }
            setSelectedEvent(closestEvent);
            setSelectedClerk(mSelectedEvent.title.toString());

            int startHour = mSelectedEvent.startTime / 60;
            int endHour;
            if (mSelectedEvent.startTime < mSelectedEvent.endTime) {
                endHour = (mSelectedEvent.endTime - 1) / 60;
            } else {
                endHour = mSelectedEvent.endTime / 60;
            }

            if (mSelectionHour < startHour) {
                setSelectedHour(startHour);
            } else if (mSelectionHour > endHour) {
                setSelectedHour(endHour);
            }
        }
    }

    /**
     * Cleanup the pop-up and timers.
     */
    public void cleanup() {
        // Protect against null-pointer exceptions
        if (mHandler != null) {
            mHandler.removeCallbacks(mUpdateCurrentTime);
        }
        // Clear all click animations
        eventClickCleanup();
        // Turn off redraw
        mRemeasure = false;
        // Turn off scrolling to make sure the view is in the correct state if we fling back to it
        mScrolling = false;
    }

    private void eventClickCleanup() {
        this.removeCallbacks(mClearClick);
        this.removeCallbacks(mSetClick);
        mClickedEvent = null;
        mSavedClickedEvent = null;
    }

    private void setSelectedEvent(Event e) {
        mSelectedEvent = e;
    }

    private void setSelectedHour(int h) {
        mSelectionHour = h;
    }
    private void setSelectedClerk(int d) {
        mSelectionClerk = d;
    }
    private void setSelectedClerk(String clerkName) {
        mSelectionClerk = getClerkIndexByName(clerkName);
    }
    private void setSelectedDay(int d) {
    	mCurrentSelectedJulianDay = d;
    }    

    /**
     * Restart the update timer
     */
    public void restartCurrentTimeUpdates() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mUpdateCurrentTime);
            mHandler.post(mUpdateCurrentTime);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cleanup();
        super.onDetachedFromWindow();
    }

    class UpdateCurrentTime implements Runnable {

        public void run() {
            long currentTime = System.currentTimeMillis();
            mCurrentTime.set(currentTime);
            //% causes update to occur on 5 minute marks (11:10, 11:15, 11:20, etc.)
            mHandler.postDelayed(mUpdateCurrentTime, UPDATE_CURRENT_TIME_DELAY - (currentTime % UPDATE_CURRENT_TIME_DELAY));
            mTodayJulianDay = Time.getJulianDay(currentTime, mCurrentTime.gmtoff);
            invalidate();
        }
    }

    class CalendarGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent ev) {
            if (DEBUG) Log.e(TAG, "GestureDetector.onSingleTapUp");
            AppointmentView.this.doSingleTapUp(ev);
            return true;
        }

        @Override
        public void onLongPress(MotionEvent ev) {
            if (DEBUG) Log.e(TAG, "GestureDetector.onLongPress");
            AppointmentView.this.doLongPress(ev);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (DEBUG) Log.e(TAG, "GestureDetector.onScroll");
            AppointmentView.this.doScroll(e1, e2, distanceX, distanceY);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (DEBUG) Log.e(TAG, "GestureDetector.onFling");
            AppointmentView.this.doFling(e1, e2, velocityX, velocityY);
            return true;
        }

        @Override
        public boolean onDown(MotionEvent ev) {
            if (DEBUG) Log.e(TAG, "GestureDetector.onDown");
            AppointmentView.this.doDown(ev);
            return true;
        }
    }
}
