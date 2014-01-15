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
package nl.lxtreme.ols.tool.uart.impl;


import java.awt.*;

import nl.lxtreme.ols.tool.api.*;


/**
 * Provides an UART/RS-232 analysis tool.
 */
public class UARTAnalyser implements Tool<UARTDataSet>
{
  // CONSTANTS

  static final String NAME = "UART analyser";

  // METHODS

  /**
   * {@inheritDoc}
   */
  @Override
  public UARTAnalyserTask createToolTask( ToolContext aContext, ToolProgressListener aProgressListener )
  {
    return new UARTAnalyserTask( aContext, aProgressListener );
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ToolCategory getCategory()
  {
    return ToolCategory.DECODER;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getName()
  {
    return NAME.concat( " ..." );
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void invoke( final Window aParent, final ToolContext aContext )
  {
    new UARTProtocolAnalysisDialog( aParent, this, aContext ).showDialog();
  }
}

/* EOF */
