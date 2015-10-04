/*
 * Copyright (c) 2015-2015 Vladimir Schneider <vladimir.schneider@gmail.com>
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.vladsch.idea.multimarkdown.language;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveResult;
import com.intellij.util.IncorrectOperationException;
import com.vladsch.idea.multimarkdown.MultiMarkdownPlugin;
import com.vladsch.idea.multimarkdown.psi.MultiMarkdownFile;
import com.vladsch.idea.multimarkdown.psi.MultiMarkdownNamedElement;
import com.vladsch.idea.multimarkdown.psi.MultiMarkdownWikiPageRef;
import com.vladsch.idea.multimarkdown.util.FileReferenceLink;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MultiMarkdownReferenceWikiPageRef extends MultiMarkdownReference {
    private static final Logger logger = Logger.getLogger(MultiMarkdownReferenceWikiPageRef.class);
    protected boolean resolveRefIsMissing;
    //private ResolveResult[] incompleteCodeResolveResults;

    public MultiMarkdownReferenceWikiPageRef(@NotNull MultiMarkdownWikiPageRef element, @NotNull TextRange textRange) {
        super(element, textRange);

        MultiMarkdownPlugin.getProjectComponent(element.getProject()).addListener(fileListListener);
    }

    @Override
    public void invalidateResolveResults() {
        super.invalidateResolveResults();
        resolveRefIsMissing = false;
    }

    public boolean isResolveRefMissing() {
        return resolveRefIsMissing;
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        // we will handle this by renaming the element to point to the new location
        if (myElement instanceof MultiMarkdownWikiPageRef && element instanceof PsiFile) {
            FileReferenceLink fileReferenceLink = new FileReferenceLink(myElement.getContainingFile(), ((PsiFile) element));
            String wikiPageRef = fileReferenceLink.getWikiPageRef();
            // this will create a new reference and loose connection to this one
            return ((MultiMarkdownWikiPageRef) myElement).setName(wikiPageRef, true);
        }
        return super.bindToElement(element);
    }

    @NotNull
    @Override
    public ResolveResult[] getMultiResolveResults(boolean incompleteCode) {
        String name = myElement.getName();
        if (name != null) {
            MultiMarkdownFile containingFile = (MultiMarkdownFile) myElement.getContainingFile();
            List<ResolveResult> results = new ArrayList<ResolveResult>();

            PsiFile[] files = MultiMarkdownPlugin.getProjectComponent(myElement.getProject()).getFileReferenceList().query()
                    .matchWikiRef((MultiMarkdownWikiPageRef) myElement)
                    .accessibleWikiPageRefs().getPsiFiles();

            if (files.length > 0) {
                containingFile.removeListener(fileListListener);
                resolveRefIsMissing = false;

                for (PsiFile file : files) {
                    results.add(new PsiElementResolveResult(file));
                    //logger.info("Reference " + resolveResults.length + " for " + myElement + ": " + resolveResults[0].getElement().hashCode());
                }
            } else {
                // this one is missing, see if there is a missing ref registered already
                resolveRefIsMissing = true;

                //logger.info("getting dummy Reference" + " for " + myElement + " named: " + myElement.getMissingElementNamespace() + name);
                MultiMarkdownNamedElement missingLinkElement = containingFile.getMissingLinkElement(myElement, myElement.getMissingElementNamespace() + name);

                //if (missingLinkElement == myElement) {
                //    logger.info("dummy Reference" + " for " + myElement + " is itself");
                //}

                //logger.info("setting dummy Reference" + " for " + myElement + " named: " + myElement.getMissingElementNamespace() + name);
                results.add(new PsiElementResolveResult(missingLinkElement));
                containingFile.addListener(fileListListener);
            }

            return results.toArray(new ResolveResult[results.size()]);
        }

        return EMPTY_RESULTS;
    }
}
