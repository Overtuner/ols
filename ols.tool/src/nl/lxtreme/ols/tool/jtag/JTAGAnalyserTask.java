/*
 * OpenBench LogicSniffer / SUMP project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin St, Fifth Floor, Boston, MA 02110, USA
 *
 *
 * Copyright (C) 2010-2011 - J.W. Janssen, http://www.lxtreme.nl
 */
package nl.lxtreme.ols.tool.jtag;


import static nl.lxtreme.ols.common.annotation.DataAnnotation.*;
import static nl.lxtreme.ols.tool.jtag.JTAGState.*;

import java.math.*;
import java.util.logging.*;

import nl.lxtreme.ols.common.acquisition.*;
import nl.lxtreme.ols.tool.api.*;


/**
 * Provides a task for decoding JTAG signals.
 *
 * @author J.W. Janssen
 * @author Mario Schrenk
 */
public class JTAGAnalyserTask implements ToolTask<JTAGDataSet>
{
  // CONSTANTS

  private static final Logger LOG = Logger.getLogger( JTAGAnalyserTask.class.getName() );

  public static final String PROPERTY_AUTO_DETECT_MODE = "AutoDetectJTAGMode";

  // VARIABLES

  private final ToolContext context;
  private final ToolAnnotationHelper annHelper;
  private final ToolProgressListener progressListener;

  private int startOfDecode;
  private int endOfDecode;

  private int tmsIdx;
  private int tckIdx;
  private int tdiIdx;
  private int tdoIdx;

  private JTAGState currentState;
  private JTAGState oldState;
  private int startIdx;

  // CONSTRUCTORS

  /**
   * @param aData
   */
  public JTAGAnalyserTask( final ToolContext aContext, final ToolProgressListener aProgressListener )
  {
    this.context = aContext;
    this.progressListener = aProgressListener;
    this.annHelper = new ToolAnnotationHelper( aContext );

    this.tdoIdx = -1;
    this.tdiIdx = -1;
  }

  // METHODS

  /**
   * This is the JTAG protocol decoder core. The decoded data are put to a
   * JTable object directly.
   *
   * @see javax.swing.SwingWorker#doInBackground()
   */
  @Override
  public JTAGDataSet call() throws Exception
  {
    if ( LOG.isLoggable( Level.FINE ) )
    {
      LOG.fine( "tmsmask = 0x" + Integer.toHexString( 1 << this.tmsIdx ) );
      LOG.fine( "tckmask = 0x" + Integer.toHexString( 1 << this.tckIdx ) );
      LOG.fine( "tdomask = 0x" + Integer.toHexString( 1 << this.tdoIdx ) );
      LOG.fine( "tdimask = 0x" + Integer.toHexString( 1 << this.tdiIdx ) );
    }

    // Initialize the channel labels + clear any existing annotations...
    prepareResults();

    final JTAGDataSet decodedData = new JTAGDataSet( this.startOfDecode, this.endOfDecode, this.context.getData() );

    // Perform the actual decoding of the data line(s)...
    clockDataOnEdge( decodedData, this.startOfDecode );

    // Sort the data on the starting timestamp...
    decodedData.sort();

    return decodedData;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getName()
  {
    return JTAGAnalyser.NAME;
  }

  /**
   * Sets the decoding area.
   * 
   * @param aStartOfDecode
   *          a start sample index, >= 0;
   * @param aEndOfDecode
   *          a ending sample index, >= 0.
   */
  public void setDecodingArea( final int aStartOfDecode, final int aEndOfDecode )
  {
    this.startOfDecode = aStartOfDecode;
    this.endOfDecode = aEndOfDecode;
  }

  /**
   * Sets the TCK channel index.
   *
   * @param aTckIndex
   *          the index of the "serial-clock" channel.
   */
  public void setTckIndex( final int aTckIndex )
  {
    this.tckIdx = aTckIndex;
  }

  /**
   * Sets the TDI channel index.
   *
   * @param aTdiMask
   *          the index of the "master-out slave-in" channel.
   */
  public void setTdiIndex( final int aTdiIndex )
  {
    this.tdiIdx = aTdiIndex;
  }

  /**
   * Sets the TDO channel index.
   *
   * @param aTdoMask
   *          the index of the "master-in slave-out" channel.
   */
  public void setTdoIndex( final int aTdoIndex )
  {
    this.tdoIdx = aTdoIndex;
  }

  /**
   * Sets the TMS channel index.
   *
   * @param aTmsMask
   *          the index of the chip-select channel.
   */
  public void setTmsIndex( final int aTmsIndex )
  {
    this.tmsIdx = aTmsIndex;
  }

  /**
   * Decodes the JTAG-data on a given clock edge.
   *
   * @param aDataSet
   *          the decoded data to fill;
   * @param aMode
   *          the JTAG mode defining the edges on which data can be sampled and
   *          on which edges data can change.
   */
  private void clockDataOnEdge( final JTAGDataSet aDataSet, final int aSlaveSelectedIdx )
  {
    final AcquisitionData data = this.context.getData();

    final int[] values = data.getValues();
    final long[] timestamps = data.getTimestamps();

    final int startOfDecode = Math.max( aSlaveSelectedIdx, aDataSet.getStartOfDecode() );
    final int endOfDecode = aDataSet.getEndOfDecode();

    final int tdoMask = ( 1 << this.tdoIdx );
    final int tdiMask = ( 1 << this.tdiIdx );
    final int tckMask = ( 1 << this.tckIdx );
    final int tmsMask = ( 1 << this.tmsIdx );

    // scanning for falling/rising clk edges
    int oldTckValue = ( values[startOfDecode] & tckMask );

    String state;
    int startTdiDataIdx = 0;
    int endTdiDataIdx = 0;

    String tdiData = null;
    String tdoData = null;

    this.currentState = TEST_LOGIC_RESET;
    this.oldState = TEST_LOGIC_RESET;
    this.startIdx = startOfDecode;

    LOG.log( Level.INFO, "clockDataOnEdge: " + startOfDecode + " to " + endOfDecode );

    final double length = endOfDecode - startOfDecode;
    for ( int idx = startOfDecode + 1; idx < endOfDecode; idx++ )
    {
      final int dataSample = values[idx];
      final int tckValue = ( dataSample & tckMask );
      final int tmsValue = ( dataSample & tmsMask );
      final int tdiValue = ( dataSample & tdiMask );
      final int tdoValue = ( dataSample & tdoMask );

      if ( oldTckValue != tckValue )
      {
        oldTckValue = tckValue;

        if ( tckValue != 0 )
        {
          if ( this.currentState == TEST_LOGIC_RESET )
          { // state 0: Test Logic Reset
            state = this.currentState.getDisplayText();
            if ( tmsValue == 0 )
            {
              this.currentState = RUN_TEST_IDLE;
            }
          }
          else if ( this.currentState == RUN_TEST_IDLE )
          { // state 1: Run Test Idle
            state = this.currentState.getDisplayText();
            if ( tmsValue == 0 )
            {
              this.currentState = RUN_TEST_IDLE;
            }
            else
            {
              this.currentState = SELECT_DR;
            }
          }
          else if ( this.currentState == SELECT_DR )
          { // state 2: Select DR
            state = this.currentState.getDisplayText();
            if ( tmsValue == 0 )
            {
              this.currentState = CAPTURE_DR;
            }
            else
            {
              this.currentState = SELECT_IR;
            }
          }
          else if ( this.currentState == CAPTURE_DR )
          { // state 3: Capture DR
            state = this.currentState.getDisplayText();

            tdiData = null;
            tdoData = null;

            if ( tmsValue == 0 )
            {
              this.currentState = SHIFT_DR;
            }
            else
            {
              this.currentState = EXIT1_DR;
            }
          }
          else if ( this.currentState == SHIFT_DR )
          { // state 4: Shift DR
            state = this.currentState.getDisplayText();

            if ( tdiData == null )
            {
              startTdiDataIdx = idx;

              tdiData = "";
              tdoData = "";
            }
            endTdiDataIdx = idx;

            if ( tdiValue == 0 )
            {
              tdiData = "0" + tdiData;
            }
            else
            {
              tdiData = "1" + tdiData;
            }

            if ( tdoValue == 0 )
            {
              tdoData = "0" + tdoData;
            }
            else
            {
              tdoData = "1" + tdoData;
            }

            if ( tmsValue != 0 )
            {
              this.currentState = EXIT1_DR;
            }
          }
          else if ( this.currentState == EXIT1_DR )
          { // state 5: Exit1 DR
            state = this.currentState.getDisplayText();
            if ( tmsValue == 0 )
            {
              this.currentState = PAUSE_DR;
            }
            else
            {
              this.currentState = UPDATE_DR;
            }
          }
          else if ( this.currentState == PAUSE_DR )
          { // state 6: Pause DR
            state = this.currentState.getDisplayText();
            if ( tmsValue == 0 )
            {
              this.currentState = PAUSE_DR;
            }
            else
            {
              this.currentState = EXIT2_DR;
            }
          }
          else if ( this.currentState == EXIT2_DR )
          { // state 7: Exit2 DR
            state = this.currentState.getDisplayText();
            if ( tmsValue == 0 )
            {
              this.currentState = SHIFT_DR;
            }
            else
            {
              this.currentState = UPDATE_DR;
            }
          }
          else if ( this.currentState == UPDATE_DR )
          { // state 8: Update DR
            state = this.currentState.getDisplayText();

            this.annHelper.addAnnotation( this.tdiIdx, timestamps[startTdiDataIdx], timestamps[endTdiDataIdx],
                new BigInteger( tdiData, 2 ), KEY_TYPE, TYPE_SYMBOL );
            this.annHelper.addAnnotation( this.tdoIdx, timestamps[startTdiDataIdx], timestamps[endTdiDataIdx],
                new BigInteger( tdoData, 2 ), KEY_TYPE, TYPE_SYMBOL );

            aDataSet.reportJTAGTdiData( this.tdiIdx, startTdiDataIdx, endTdiDataIdx, this.currentState, tdiData );
            aDataSet.reportJTAGTdoData( this.tdoIdx, startTdiDataIdx, endTdiDataIdx, this.currentState, tdoData );

            if ( tmsValue == 0 )
            {
              this.currentState = RUN_TEST_IDLE;
            }
            else
            {
              this.currentState = SELECT_DR;
            }
          }
          else if ( this.currentState == SELECT_IR )
          { // state 9: Select IR
            state = this.currentState.getDisplayText();
            if ( tmsValue == 0 )
            {
              this.currentState = CAPTURE_IR;
            }
            else
            {
              this.currentState = TEST_LOGIC_RESET;
            }
          }
          else if ( this.currentState == CAPTURE_IR )
          { // state 10: Capture IR
            state = this.currentState.getDisplayText();

            tdiData = null;
            tdoData = null;

            if ( tmsValue == 0 )
            {
              this.currentState = SHIFT_IR;
            }
            else
            {
              this.currentState = EXIT1_IR;
            }
          }
          else if ( this.currentState == SHIFT_IR )
          { // state 11: Shift IR
            state = this.currentState.getDisplayText();

            if ( tdiData == null )
            {
              startTdiDataIdx = idx;

              tdiData = "";
              tdoData = "";
            }
            endTdiDataIdx = idx;

            if ( tdiValue == 0 )
            {
              tdiData = "0" + tdiData;
            }
            else
            {
              tdiData = "1" + tdiData;
            }

            if ( tdoValue == 0 )
            {
              tdoData = "0" + tdoData;
            }
            else
            {
              tdoData = "1" + tdoData;
            }

            if ( tmsValue != 0 )
            {
              this.currentState = EXIT1_IR;
            }
          }
          else if ( this.currentState == EXIT1_IR )
          { // state 12: Exit1 IR
            state = this.currentState.getDisplayText();
            if ( tmsValue == 0 )
            {
              this.currentState = PAUSE_IR;
            }
            else
            {
              this.currentState = UPDATE_IR;
            }
          }
          else if ( this.currentState == PAUSE_IR )
          { // state 13: Pause IR
            state = this.currentState.getDisplayText();
            if ( tmsValue == 0 )
            {
              this.currentState = PAUSE_IR;
            }
            else
            {
              this.currentState = EXIT2_IR;
            }
          }
          else if ( this.currentState == EXIT2_IR )
          { // state 14: Exit2 IR
            state = this.currentState.getDisplayText();
            if ( tmsValue == 0 )
            {
              this.currentState = SHIFT_IR;
            }
            else
            {
              this.currentState = UPDATE_IR;
            }
          }
          else if ( this.currentState == UPDATE_IR )
          { // state 15: Update IR
            state = this.currentState.getDisplayText();

            this.annHelper.addAnnotation( this.tdiIdx, timestamps[startTdiDataIdx], timestamps[endTdiDataIdx],
                new BigInteger( tdiData, 2 ), KEY_TYPE, TYPE_SYMBOL );

            this.annHelper.addAnnotation( this.tdoIdx, timestamps[startTdiDataIdx], timestamps[endTdiDataIdx],
                new BigInteger( tdoData, 2 ), KEY_TYPE, TYPE_SYMBOL );

            aDataSet.reportJTAGTdiData( this.tdiIdx, startTdiDataIdx, endTdiDataIdx, this.currentState, tdiData );
            aDataSet.reportJTAGTdoData( this.tdoIdx, startTdiDataIdx, endTdiDataIdx, this.currentState, tdoData );

            if ( tmsValue == 0 )
            {
              this.currentState = RUN_TEST_IDLE;
            }
            else
            {
              this.currentState = SELECT_DR;
            }
          }
          else
          {
            state = "ERROR";
          }

          if ( this.oldState != this.currentState )
          {
            this.annHelper.addEventAnnotation( this.tmsIdx, timestamps[this.startIdx], timestamps[idx], state,
                KEY_COLOR, "#e0e0e0" );

            aDataSet.reportJTAGState( this.tmsIdx, this.startIdx, idx, this.oldState );

            this.startIdx = idx + 1;
            this.oldState = this.currentState;
          }

          this.progressListener.setProgress( ( int )( ( ( idx - startOfDecode ) * 100.0 ) / length ) );
        }
      }
    }
  }

  /**
   * Determines the channel labels that are used in the annotations and reports
   * and clears any existing annotations on the decoded channels.
   */
  private void prepareResults()
  {
    if ( this.tckIdx >= 0 )
    {
      this.annHelper.clearAnnotations( this.tckIdx );
      this.annHelper.addLabelAnnotation( this.tckIdx, JTAGDataSet.JTAG_TCK );
    }
    if ( this.tmsIdx >= 0 )
    {
      this.annHelper.clearAnnotations( this.tmsIdx );
      this.annHelper.addLabelAnnotation( this.tmsIdx, JTAGDataSet.JTAG_TMS );
    }
    if ( this.tdiIdx >= 0 )
    {
      this.annHelper.clearAnnotations( this.tdiIdx );
      this.annHelper.addLabelAnnotation( this.tdiIdx, JTAGDataSet.JTAG_TDI );
    }
    if ( this.tdoIdx >= 0 )
    {
      this.annHelper.clearAnnotations( this.tdoIdx );
      this.annHelper.addLabelAnnotation( this.tdoIdx, JTAGDataSet.JTAG_TDO );
    }
  }
}
