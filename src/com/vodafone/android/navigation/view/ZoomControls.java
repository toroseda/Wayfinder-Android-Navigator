/*******************************************************************************
 * Copyright (c) 1999-2010, Vodafone Group Services
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions 
 * are met:
 * 
 *     * Redistributions of source code must retain the above copyright 
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above 
 *       copyright notice, this list of conditions and the following 
 *       disclaimer in the documentation and/or other materials provided 
 *       with the distribution.
 *     * Neither the name of Vodafone Group Services nor the names of its 
 *       contributors may be used to endorse or promote products derived 
 *       from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING 
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
 * OF SUCH DAMAGE.
 ******************************************************************************/
package com.vodafone.android.navigation.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ZoomButton;

import com.vodafone.android.navigation.R;

public class ZoomControls extends LinearLayout {

    private ZoomButton btnZoomIn;
    private ZoomButton btnZoomOut;

    public ZoomControls(Context context) {
        super(context);
        this.init(context);
    }

    public ZoomControls(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.init(context);
    }

    private void init(Context context) {
        View.inflate(context, R.layout.zoom_controls, this);
        this.btnZoomIn = (ZoomButton) this.findViewById(R.id.button_zoom_in);
        this.btnZoomIn.setFocusable(false);
        this.btnZoomIn.setFocusableInTouchMode(false);
        this.btnZoomIn.setClickable(false);

        this.btnZoomOut = (ZoomButton) this.findViewById(R.id.button_zoom_out);
        this.btnZoomOut.setFocusable(false);
        this.btnZoomOut.setFocusableInTouchMode(false);
        this.btnZoomOut.setClickable(false);
    }

    public void setOnZoomInListeners(OnTouchListener onTouchListener) {
        this.btnZoomIn.setOnTouchListener(onTouchListener);
    }
    
    public void setOnZoomOutListeners(OnTouchListener onTouchListener) {
        this.btnZoomOut.setOnTouchListener(onTouchListener);
    }

    public void show() {
        this.setVisibility(View.VISIBLE);
    }

    public void hide() {
        this.setVisibility(View.GONE);
    }

    public void setIsZoomInEnabled(boolean enabled) {
        this.btnZoomIn.setEnabled(enabled);
    }

    public void setIsZoomOutEnabled(boolean enabled) {
        this.btnZoomOut.setEnabled(enabled);
    }
}
