package com.android.tests.overlay2;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.widget.ImageView;


public class MainTest extends ActivityInstrumentationTestCase2<Main> {
    
    private final static int GREEN = 0xFF00FF00;

    private ImageView mNoOverlayIV;
    private ImageView mTypeOverlayIV;
    private ImageView mFlavorOverlayIV;
    private ImageView mTypeFlavorOverlayIV;
    private ImageView mVariantTypeFlavorOverlayIV;

    /**
     * Creates an {@link ActivityInstrumentationTestCase2} that tests the {@link Main} activity.
     */
    public MainTest() {
        super(Main.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Main a = getActivity();
        // ensure a valid handle to the activity has been returned
        assertNotNull(a);
        mNoOverlayIV = (ImageView) a.findViewById(R.id.no_overlay);
        mTypeOverlayIV = (ImageView) a.findViewById(R.id.type_overlay);
        mFlavorOverlayIV = (ImageView) a.findViewById(R.id.flavor_overlay);
        mTypeFlavorOverlayIV = (ImageView) a.findViewById(R.id.type_flavor_overlay);
        mVariantTypeFlavorOverlayIV = (ImageView) a.findViewById(R.id.variant_type_flavor_overlay);
    }

    /**
     * The name 'test preconditions' is a convention to signal that if this
     * test doesn't pass, the test case was not set up properly and it might
     * explain any and all failures in other tests.  This is not guaranteed
     * to run before other tests, as junit uses reflection to find the tests.
     */
    @MediumTest
    public void testPreconditions() {
        assertNotNull(mNoOverlayIV);
        assertNotNull(mTypeOverlayIV);
        assertNotNull(mFlavorOverlayIV);
        assertNotNull(mTypeFlavorOverlayIV);
        assertNotNull(mVariantTypeFlavorOverlayIV);
    }

    public void testNoOverlay() {
        pixelLooker(mNoOverlayIV, GREEN);
    }

    public void testTypeOverlay() {
        pixelLooker(mTypeOverlayIV, GREEN);
    }

    public void testFlavorOverlay() {
        pixelLooker(mFlavorOverlayIV, GREEN);
    }

    public void testTypeFlavorOverlay() {
        pixelLooker(mTypeFlavorOverlayIV, GREEN);
    }

    public void testVariantTypeFlavorOverlay() {
        pixelLooker(mVariantTypeFlavorOverlayIV, GREEN);
    }
    
    private void pixelLooker(ImageView iv, int expectedColor) {
        BitmapDrawable d = (BitmapDrawable) iv.getDrawable();
        Bitmap bitmap = d.getBitmap();
        assertEquals(expectedColor, bitmap.getPixel(0, 0));
    }
}

