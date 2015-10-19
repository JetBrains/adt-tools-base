/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.ide.common.vectordrawable;

import org.w3c.dom.Node;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represent a SVG file's group element.
 */
class SvgGroupNode extends SvgNode {
    private static Logger logger = Logger.getLogger(SvgGroupNode.class.getSimpleName());
    private static final String INDENT_LEVEL = "    ";

    private ArrayList<SvgNode> mChildren = new ArrayList<SvgNode>();

    public SvgGroupNode(SvgTree svgTree, Node docNode, String name) {
        super(svgTree, docNode, name);
    }

    public void addChild(SvgNode child) {
        // Pass the presentation map down to the children, who can override the attributes.
        mChildren.add(child);
        // The child has its own attributes map. But the parents can still fill some attributes
        // if they don't exists
        child.fillEmptyAttributes(mVdAttributesMap);
    }

    @Override
    public void dumpNode(String indent) {
        // Print the current group.
        logger.log(Level.FINE, indent + "current group is :" + getName());

        // Then print all the children.
        for (SvgNode node : mChildren) {
            node.dumpNode(indent + INDENT_LEVEL);
        }
    }

    @Override
    public boolean isGroupNode() {
        return true;
    }

    @Override
    public void transformIfNeeded(AffineTransform rootTransform) {
        for (SvgNode p : mChildren) {
            p.transformIfNeeded(rootTransform);
        }
    }

    @Override
    public void flattern(AffineTransform transform) {
        for (SvgNode n : mChildren) {
            mStackedTransform.setTransform(transform);
            mStackedTransform.concatenate(mLocalTransform);
            n.flattern(mStackedTransform);
        }
    }

    @Override
    public void writeXML(OutputStreamWriter writer) throws IOException {
        for (SvgNode node : mChildren) {
            node.writeXML(writer);
        }
    }
}
