/**
 * Copyright (c) 2013-2016 by Brainwy Software Ltda. All Rights Reserved.
 * Licensed under the terms of the Eclipse Public License (EPL).
 * Please see the license.txt included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package org.brainwy.liclipsetext.editor.partitioning;

import java.util.Queue;

import org.brainwy.liclipsetext.shared_core.log.Log;
import org.brainwy.liclipsetext.shared_core.partitioner.IContentsScanner;
import org.brainwy.liclipsetext.shared_core.partitioner.IDocumentScanner;
import org.brainwy.liclipsetext.shared_core.partitioner.IMarkScanner;
import org.brainwy.liclipsetext.shared_core.partitioner.IScannerWithOffPartition;
import org.brainwy.liclipsetext.shared_core.partitioner.PartitionCodeReader;
import org.brainwy.liclipsetext.shared_core.partitioner.SubRuleToken;
import org.brainwy.liclipsetext.shared_core.string.FastStringBuffer;
import org.brainwy.liclipsetext.shared_core.structure.FastStack;
import org.brainwy.liclipsetext.shared_core.structure.LinkedListWarningOnSlowOperations;
import org.brainwy.liclipsetext.shared_core.structure.Tuple;
import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.Token;

public class ScannerRange
        implements ICharacterScanner, IDocumentScanner, IMarkScanner, IContentsScanner, IScannerWithOffPartition {

    /**
     * Fields written when the nextToken is computed.
     */
    private IToken fToken;
    private SubRuleToken fSubRuleToken;

    public IToken getToken() {
        if (fToken == null) {
            Log.log("Token is still null (wasn't it computed?)");
            return new Token(null);
        }
        return fToken;
    }

    public void setToken(IToken token) {
        if (token == null) {
            throw new AssertionError("Token cannot be null.");
        }
        this.fToken = token;
    }

    public SubRuleToken getSubRuleToken() {
        return fSubRuleToken;
    }

    public void setSubRuleToken(SubRuleToken subRuleToken) {
        this.fSubRuleToken = subRuleToken;
    }

    public void startNextToken() {
        fTokenOffset = fOffset;
        fColumn = UNDEFINED;
        fToken = null;
        fSubRuleToken = null;
    }

    private final IPartitionCodeReaderInScannerHelper helper;

    public ScannerRange(IDocument doc, int offset, int length, IPartitionCodeReaderInScannerHelper helper) {
        helper.setDocument(doc);
        this.fDocument = doc;
        this.helper = helper;
        this.setRange(doc, offset, length);
    }

    // Used in place of setPartialRange.
    public ScannerRange(IDocument doc, int offset, int length, String contentType, int partitionOffset,
            IPartitionCodeReaderInScannerHelper helper) {
        this.helper = helper;
        this.fDocument = doc;
        helper.setDocument(doc);
        setPartialRange(doc, offset, length, contentType, partitionOffset);
    }

    /**
     * Sets the buffer to the given number of characters.
     *
     * @param size the buffer size
     */
    protected void setBufferSize(int size) {
        Assert.isTrue(size > 0);
        fBufferSize = size;
        fBuffer = new char[size];
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the given content type is not <code>null</code> then this scanner will first try the rules
     * that match the given content type.
     * </p>
     */
    public void setPartialRange(IDocument document, int offset, int length, String contentType, int partitionOffset) {
        fContentType = contentType;
        fPartitionOffset = partitionOffset;
        if (partitionOffset > -1) {
            int delta = offset - partitionOffset;
            if (delta > 0) {
                setRange(document, partitionOffset, length + delta);
                fOffset = offset;
                return;
            }
        }
        setRange(document, offset, length);
    }

    // Parsing state

    /** The document to be scanned */
    protected IDocument fDocument;
    /** The cached legal line delimiters of the document */
    protected char[][] fDelimiters;
    /** The offset of the next character to be read */
    protected int fOffset;
    /** The end offset of the range to be scanned */
    protected int fRangeEnd;
    /** The offset of the last read token */
    protected int fTokenOffset;
    /** The cached column of the current scanner position */
    protected int fColumn;
    /** Internal setting for the un-initialized column cache. */
    protected static final int UNDEFINED = -1;

    /*
     * @see ITokenScanner#setRange(IDocument, int, int)
     */
    public void setRange(final IDocument document, int offset, int length) {
        fDocumentLength = document.getLength();
        shiftBuffer(offset);

        Assert.isLegal(document != null);
        final int documentLength = document.getLength();
        checkRange(offset, length, documentLength);

        fDocument = document;
        fOffset = offset;
        fColumn = UNDEFINED;
        fRangeEnd = offset + length;

        String[] delimiters = fDocument.getLegalLineDelimiters();
        fDelimiters = new char[delimiters.length][];
        for (int i = 0; i < delimiters.length; i++) {
            fDelimiters[i] = delimiters[i].toCharArray();
        }
    }

    /**
     * Checks that the given range is valid.
     * See https://bugs.eclipse.org/bugs/show_bug.cgi?id=69292
     *
     * @param offset the offset of the document range to scan
     * @param length the length of the document range to scan
     * @param documentLength the document's length
     * @since 3.3
     */
    private void checkRange(int offset, int length, int documentLength) {
        Assert.isLegal(offset > -1);
        Assert.isLegal(length > -1);
        Assert.isLegal(offset + length <= documentLength);
    }

    /*
     * @see ITokenScanner#getTokenOffset()
     */
    public int getTokenOffset() {
        if (this.currentOfferedSubToken != null) {
            return this.currentOfferedSubToken.offset;
        }
        return fTokenOffset;
    }

    /*
     * @see ITokenScanner#getTokenLength()
     */
    public int getTokenLength() {
        if (this.currentOfferedSubToken != null) {
            return this.currentOfferedSubToken.len;
        }
        if (fOffset < fRangeEnd) {
            return fOffset - getTokenOffset();
        }
        return fRangeEnd - getTokenOffset();
    }

    /*
     * @see ICharacterScanner#getColumn()
     */
    public int getColumn() {
        if (fColumn == UNDEFINED) {
            try {
                int line = fDocument.getLineOfOffset(fOffset);
                int start = fDocument.getLineOffset(line);

                fColumn = fOffset - start;

            } catch (BadLocationException ex) {
            }
        }
        return fColumn;
    }

    /*
     * @see ICharacterScanner#getLegalLineDelimiters()
     */
    public char[][] getLegalLineDelimiters() {
        return fDelimiters;
    }

    /** The content type of the partition in which to resume scanning. */
    protected String fContentType;
    /** The offset of the partition inside which to resume. */
    protected int fPartitionOffset;

    /*
     * (non-Javadoc)
     * @see org.brainwy.liclipsetext.editor.epl.rules.IDocumentScanner#getDocument()
     */
    public IDocument getDocument() {
        return fDocument;
    }

    /** The default buffer size. Value = 2000 -- note: default was 500 in original */
    private final static int DEFAULT_BUFFER_SIZE = 2000;
    /** The actual size of the buffer. Initially set to <code>DEFAULT_BUFFER_SIZE</code> */
    private int fBufferSize = DEFAULT_BUFFER_SIZE;
    /** The buffer */
    private char[] fBuffer = new char[DEFAULT_BUFFER_SIZE];
    /** The offset of the document at which the buffer starts */
    private int fStart;
    /** The offset of the document at which the buffer ends */
    private int fEnd;
    /** The cached length of the document */
    private int fDocumentLength;

    private int lastRegexpMatchOffset;

    public void setLastRegexpMatchOffset(int endOffset) {
        this.lastRegexpMatchOffset = endOffset;
    }

    public int getLastRegexpMatchOffset() {
        return lastRegexpMatchOffset;
    }

    /**
     * Shifts the buffer so that the buffer starts at the
     * given document offset.
     *
     * @param offset the document offset at which the buffer starts
     */
    private void shiftBuffer(int offset) {

        fStart = offset;
        fEnd = fStart + fBufferSize;
        if (fEnd > fDocumentLength) {
            fEnd = fDocumentLength;
        }

        try {

            String content = fDocument.get(fStart, fEnd - fStart);
            content.getChars(0, fEnd - fStart, fBuffer, 0);

        } catch (BadLocationException x) {
        }
    }

    public int getMark() {
        return fOffset;
    }

    public void getContents(int offset, int length, FastStringBuffer buffer) {
        buffer.resizeForMinimum(buffer.length() + length);
        int mark = this.getMark();
        this.setMark(offset);
        try {
            for (int i = 0; i < length; i++) {
                buffer.append((char) this.read());
            }
        } finally {
            this.setMark(mark);
        }
    }

    public void setMark(int offset) {
        fOffset = offset;
        fColumn = UNDEFINED;

        if (fOffset == fStart) {
            shiftBuffer(Math.max(0, fStart - (fBufferSize / 2)));

        } else if (fOffset == fEnd) {
            shiftBuffer(fEnd);

        } else if (fOffset < fStart || fEnd < fOffset) {
            shiftBuffer(fOffset);

        }
    }

    // Support for temporarily pushing a sub-range during a partitioning.
    private FastStack<TempStacked> rangeStack = new FastStack<>(3);

    private static class TempStacked {

        private int offset;
        private int rangeEnd;
        private int lastRegexpMatchOffset;

        public TempStacked(int offset, int rangeEnd, int lastRegexpMatchOffset) {
            this.offset = offset;
            this.rangeEnd = rangeEnd;
            this.lastRegexpMatchOffset = lastRegexpMatchOffset;
        }

    }

    public void pushRange(int offset, int len) {
        rangeStack.push(new TempStacked(fOffset, fRangeEnd, lastRegexpMatchOffset));
        this.fOffset = offset;
        this.fRangeEnd = offset + len;
        this.setMark(fOffset);
    }

    public void popRange() {
        TempStacked pop = rangeStack.pop();
        this.fOffset = pop.offset;
        this.fRangeEnd = pop.rangeEnd;
        //Although it's not changed at push, it must be restored.
        this.lastRegexpMatchOffset = pop.lastRegexpMatchOffset;
        this.setMark(fOffset);
    }

    /*
     * @see RuleBasedScanner#read()
     */
    @Override
    public int read() {
        fColumn = UNDEFINED;
        if (fOffset >= fRangeEnd) {
            ++fOffset;
            return EOF;
        }

        if (fOffset == fEnd) {
            shiftBuffer(fEnd);
        } else if (fOffset < fStart || fEnd < fOffset) {
            shiftBuffer(fOffset);
        }

        return fBuffer[fOffset++ - fStart];
    }

    /*
     * @see RuleBasedScanner#unread()
     */
    @Override
    public void unread() {

        if (fOffset == fStart) {
            shiftBuffer(Math.max(0, fStart - (fBufferSize / 2)));
        }

        --fOffset;
        fColumn = UNDEFINED;
    }

    public PartitionCodeReader getOffPartitionCodeReader(int currOffset) {
        return helper.getOffPartitionCodeReader(currOffset);
    }

    public String getContentFromOffsetToEndOfDoc(int currOffset) {
        try {
            return this.fDocument.get(currOffset, this.fDocument.getLength() - currOffset);
        } catch (BadLocationException e) {
            Log.log(e);
            return "";
        }
    }

    public Tuple<Utf8WithCharLen, Integer> getLineFromOffsetAsBytes(int currOffset) {
        return helper.getLineFromOffsetAsBytes(currOffset);
    }

    public Tuple<Utf8WithCharLen, Integer> getLineFromLineAsBytes(int currLine) {
        return helper.getLineFromLineAsBytes(currLine);
    }

    public int getNumberOfLines() {
        return helper.getNumberOfLines();
    }

    public int getLineFromOffset(int offset) throws BadLocationException {
        return helper.getLineFromOffset(offset);
    }

    public void setInBeginWhile(boolean b) {
        helper.setInInBeginWhile(b);
    }

    public boolean isInBeginWhile() {
        return helper.isInBeginWhile();
    }

    private final Queue<SubRuleToken> cachedSubTokens = new LinkedListWarningOnSlowOperations<SubRuleToken>();

    private SubRuleToken currentOfferedSubToken;

    public boolean nextOfferedToken() {
        SubRuleToken poll = cachedSubTokens.poll();
        if (poll != null) {
            this.fToken = poll.token;
            this.currentOfferedSubToken = poll;
            return true;
        }
        this.currentOfferedSubToken = null;
        this.fToken = null;
        return false;
    }

    public void setCurrentSubToken(SubRuleToken subToken) {
        this.currentOfferedSubToken = subToken;
        this.fToken = subToken.token;
    }

    public void offerSubToken(SubRuleToken sub2) {
        this.cachedSubTokens.offer(sub2);
    }

    // ----- Matching the end rule scope

    public final FastStack<IPredicateRule> beginEndRuleStack = new FastStack<>(15);

    public void pushBeginEndRule(IPredicateRule tmBeginEndRule) {
        beginEndRuleStack.push(tmBeginEndRule);
    }

    public void popBeginEndRule() {
        beginEndRuleStack.pop();
    }

    private EndRuleMatchFromStack endRuleMatchFromStack;

    public static class EndRuleMatchFromStack {

        public final IPredicateRule endRule;
        public final SubRuleToken endRuleRegion;
        public final int initialMark;
        public final int finalMark;

        public EndRuleMatchFromStack(int initialMark, int finalMark, IPredicateRule endRule,
                SubRuleToken endRuleRegion) {
            this.initialMark = initialMark;
            this.finalMark = finalMark;
            this.endRule = endRule;
            this.endRuleRegion = endRuleRegion;
        }
    }

    public void setEndRuleMatchFromStack(int initialMark, int finalMark, IPredicateRule rule,
            SubRuleToken endRuleRegion) {
        this.endRuleMatchFromStack = new EndRuleMatchFromStack(initialMark, finalMark, rule, endRuleRegion);
    }

    public EndRuleMatchFromStack getEndRuleMatchFromStack() {
        return endRuleMatchFromStack;
    }

    public void clearEndRuleMatchFromStack() {
        endRuleMatchFromStack = null;
    }

}
