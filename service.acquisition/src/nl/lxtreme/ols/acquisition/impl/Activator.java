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
 * Copyright (C) 2006-2010 Michael Poppitz, www.sump.org
 * Copyright (C) 2010 J.W. Janssen, www.lxtreme.nl
 */
package nl.lxtreme.ols.acquisition.impl;


import java.util.*;

import nl.lxtreme.ols.acquisition.*;
import nl.lxtreme.ols.device.api.*;
import nl.lxtreme.ols.task.execution.*;

import org.apache.felix.dm.*;
import org.osgi.framework.*;
import org.osgi.service.event.*;
import org.osgi.service.log.*;


/**
 * Provides a bundle activator for the acquisition service.
 */
public class Activator extends DependencyActivatorBase
{
  // METHODS

  /**
   * {@inheritDoc}
   */
  @Override
  public void destroy( final BundleContext aContext, final DependencyManager aManager ) throws Exception
  {
    // No-op
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void init( final BundleContext aContext, final DependencyManager aManager ) throws Exception
  {
    Properties props = new Properties();
    props.put( EventConstants.EVENT_TOPIC, TaskConstants.TOPIC_TASK_EXECUTION_BASE.concat( "/*" ) );
    props.put( EventConstants.EVENT_FILTER, "(type=acquisition)" );

    String[] interfaces = new String[] { AcquisitionService.class.getName(), EventHandler.class.getName() };

    aManager.add( createComponent() //
        .setInterface( interfaces, props ) //
        .setImplementation( BackgroundDataAcquisitionService.class ) //
        .add( createServiceDependency() //
            .setService( EventAdmin.class ) //
            .setRequired( true ) ) //
        .add( createServiceDependency() //
            .setService( TaskExecutionService.class ) //
            .setRequired( true ) ) //
        .add( createServiceDependency() //
            .setService( LogService.class ) //
            .setRequired( false ) ) //
        .add( createServiceDependency() //
            .setService( Device.class ) //
            .setCallbacks( "addDevice", "removeDevice" ) //
            .setRequired( false ) ) //
        );
  }
}
