/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006 inavare GmbH (http://inavare.com)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.openscada.ae.client.test.views;

import java.util.Observable;
import java.util.Observer;
import java.util.Set;

import org.apache.log4j.Logger;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.jface.window.SameShellProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IViewActionDelegate;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PropertyDialogAction;
import org.eclipse.ui.part.DrillDownAdapter;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.views.properties.PropertySheetPage;
import org.openscada.ae.client.test.ISharedImages;
import org.openscada.ae.client.test.actions.ConnectStorageAction;
import org.openscada.ae.client.test.impl.StorageConnection;
import org.openscada.ae.client.test.impl.StorageQuery;
import org.openscada.ae.client.test.impl.StorageRepository;

/**
 * This sample class demonstrates how to plug-in a new
 * workbench view. The view shows data obtained from the
 * model. The sample creates a dummy model on the fly,
 * but a real implementation would connect to the model
 * available either in this or another plug-in (e.g. the workspace).
 * The view is connected to the model using a content provider.
 * <p>
 * The view uses a label provider to define how model
 * objects should be presented in the view. Each
 * view can present the same model objects using
 * different labels and icons, if needed. Alternatively,
 * a single label provider can be shared between views
 * in order to ensure that objects of the same type are
 * presented in the same way everywhere.
 * <p>
 */

public class StorageView extends ViewPart implements Observer
{
    public static final String VIEW_ID = "org.openscada.ae.client.test.views.StorageView";

    private static Logger _log = Logger.getLogger ( StorageView.class );

    private TreeViewer _viewer;

    private DrillDownAdapter drillDownAdapter;

    private IViewActionDelegate connectAction;

    private Action propertiesAction;

    private final StorageRepository _repository;

    class ViewContentProvider implements IStructuredContentProvider, ITreeContentProvider
    {

        private Viewer _viewer = null;

        private StorageRepository _repository = null;

        public ViewContentProvider ()
        {
        }

        public void inputChanged ( final Viewer v, final Object oldInput, final Object newInput )
        {
            clearInput ();

            this._viewer = v;
            if ( newInput instanceof StorageRepository )
            {
                this._repository = (StorageRepository)newInput;
            }
        }

        public void clearInput ()
        {
            if ( this._repository != null )
            {
                this._repository = null;
            }
        }

        public void dispose ()
        {
            clearInput ();
        }

        public Object[] getElements ( final Object parent )
        {
            if ( parent.equals ( getViewSite () ) )
            {
                return getChildren ( this._repository );
            }
            return getChildren ( parent );
        }

        public Object getParent ( final Object child )
        {
            if ( child instanceof StorageConnection )
            {
                return this._repository;
            }
            /*
            else if ( child instanceof HiveItem )
            {
                return ((HiveItem)child).getConnection();
            }
            else if ( child instanceof BrowserEntry )
            {
                return ((BrowserEntry)child).getParent ();
            }*/
            return null;
        }

        public Object[] getChildren ( final Object parent )
        {
            if ( parent instanceof StorageRepository )
            {
                return ( (StorageRepository)parent ).getConnections ().toArray ( new StorageConnection[0] );
            }
            else if ( parent instanceof StorageConnection )
            {
                final Set<StorageQuery> queries = ( (StorageConnection)parent ).getQueries ();
                if ( queries == null )
                {
                    return new String[] { "Loading..." };
                }
                return queries.toArray ( new StorageQuery[queries.size ()] );
            }
            return new Object[0];
        }

        public boolean hasChildren ( final Object parent )
        {
            if ( parent instanceof StorageRepository )
            {
                return ( (StorageRepository)parent ).getConnections ().size () > 0;
            }
            else if ( parent instanceof StorageConnection )
            {
                final Set<StorageQuery> queries = ( (StorageConnection)parent ).getQueries ();
                if ( queries == null )
                {
                    return true; // the loading string
                }
                return !queries.isEmpty ();
            }
            return false;
        }

    }

    class ViewLabelProvider extends LabelProvider
    {

        @Override
        public String getText ( final Object obj )
        {
            if ( obj instanceof StorageConnection )
            {
                final StorageConnection connection = (StorageConnection)obj;
                return connection.getConnectionInformation ().getHost () + ":" + connection.getConnectionInformation ().getPort () + " (" + connection.getConnection ().getState ().toString () + ")";
            }
            else if ( obj instanceof StorageQuery )
            {
                return ( (StorageQuery)obj ).getQueryDescription ().getId ();
            }
            return obj.toString ();
        }

        @Override
        public Image getImage ( final Object obj )
        {
            String imageKey;

            if ( obj instanceof StorageConnection )
            {
                final StorageConnection connection = (StorageConnection)obj;
                if ( connection.isConnectionRequested () )
                {
                    switch ( connection.getConnection ().getState () )
                    {
                    case CLOSED:
                        imageKey = ISharedImages.IMG_HIVE_DISCONNECTED;
                        break;
                    case CONNECTED:
                        imageKey = ISharedImages.IMG_HIVE_CONNECTION;
                        break;
                    case BOUND:
                        imageKey = ISharedImages.IMG_HIVE_CONNECTED;
                        break;
                    default:
                        imageKey = ISharedImages.IMG_HIVE_DISCONNECTED;
                        break;
                    }
                }
                else
                {
                    imageKey = ISharedImages.IMG_HIVE_CONNECTION;
                }
            }
            /*
            else if ( obj instanceof DataItemEntry )
            {
                DataItemEntry hiveItem = (DataItemEntry)obj;
                EnumSet<IODirection> io = hiveItem.getIoDirection ();
                if ( io.containsAll ( EnumSet.of ( IODirection.INPUT, IODirection.OUTPUT ) ))
                    imageKey = ISharedImages.IMG_HIVE_ITEM_IO;
                else if ( io.contains ( IODirection.INPUT ) )
                    imageKey = ISharedImages.IMG_HIVE_ITEM_I;
                else if ( io.contains ( IODirection.OUTPUT ) )
                    imageKey = ISharedImages.IMG_HIVE_ITEM_O;
                else
                    imageKey = ISharedImages.IMG_HIVE_ITEM;
            }
            else if ( obj instanceof FolderEntry )
                imageKey = ISharedImages.IMG_HIVE_FOLDER;
                */
            else
            {
                return PlatformUI.getWorkbench ().getSharedImages ().getImage ( org.eclipse.ui.ISharedImages.IMG_OBJ_ELEMENT );
            }

            return org.openscada.ae.client.test.Activator.getDefault ().getImageRegistry ().get ( imageKey );
        }
    }

    class NameSorter extends ViewerSorter
    {
    }

    /**
     * The constructor.
     */
    public StorageView ()
    {
        this._repository = org.openscada.ae.client.test.Activator.getRepository ();
        this._repository.addObserver ( this );
        registerAllConnections ();
    }

    @Override
    public void dispose ()
    {
        unregisterAllConnections ();
        this._repository.deleteObserver ( this );
        super.dispose ();
    }

    public void update ( final Observable o, final Object arg )
    {
        _log.debug ( "Update: " + o + " / " + arg );
        if ( o == this._repository )
        {
            triggerUpdateRepository ( null );
        }
        else if ( o instanceof StorageConnection )
        {
            triggerUpdateRepository ( o );
        }
    }

    public void triggerUpdateRepository ( final Object arg0 )
    {
        if ( !this._viewer.getControl ().isDisposed () )
        {
            this._viewer.getControl ().getDisplay ().asyncExec ( new Runnable () {

                public void run ()
                {
                    if ( !StorageView.this._viewer.getControl ().isDisposed () )
                    {
                        performUpdateRepository ( arg0 );
                    }
                }
            } );
        }
    }

    private void performUpdateRepository ( final Object arg0 )
    {
        _log.debug ( "Perform update on: " + arg0 );
        if ( arg0 == null )
        {
            unregisterAllConnections ();
            this._viewer.refresh ( true );
            registerAllConnections ();
        }
        else
        {
            this._viewer.refresh ( arg0, true );
        }
    }

    /**
     * This is a callback that will allow us
     * to create the viewer and initialize it.
     */
    @Override
    public void createPartControl ( final Composite parent )
    {
        this._viewer = new TreeViewer ( parent, SWT.H_SCROLL | SWT.V_SCROLL );
        this.drillDownAdapter = new DrillDownAdapter ( this._viewer );
        this._viewer.setContentProvider ( new ViewContentProvider () );
        this._viewer.setLabelProvider ( new ViewLabelProvider () );
        this._viewer.setSorter ( new NameSorter () );
        this._viewer.setInput ( this._repository );

        makeActions ();
        hookContextMenu ();
        hookDoubleClickAction ();
        contributeToActionBars ();

        getViewSite ().setSelectionProvider ( this._viewer );
    }

    private void hookContextMenu ()
    {
        final MenuManager menuMgr = new MenuManager ( "#PopupMenu" );
        menuMgr.setRemoveAllWhenShown ( true );
        menuMgr.addMenuListener ( new IMenuListener () {
            public void menuAboutToShow ( final IMenuManager manager )
            {
                StorageView.this.fillContextMenu ( manager );
            }
        } );
        final Menu menu = menuMgr.createContextMenu ( this._viewer.getControl () );
        this._viewer.getControl ().setMenu ( menu );
        getSite ().registerContextMenu ( menuMgr, this._viewer );
    }

    private void contributeToActionBars ()
    {
        final IActionBars bars = getViewSite ().getActionBars ();
        fillLocalPullDown ( bars.getMenuManager () );
        fillLocalToolBar ( bars.getToolBarManager () );
    }

    private void fillLocalPullDown ( final IMenuManager manager )
    {
        //manager.add(connectAction);
        //manager.add(new Separator());
    }

    private void fillContextMenu ( final IMenuManager manager )
    {
        //manager.add(connectAction);
        //manager.add(new Separator());
        //drillDownAdapter.addNavigationActions ( manager );
        // Other plug-ins can contribute there actions here

        manager.add ( new Separator ( IWorkbenchActionConstants.MB_ADDITIONS ) );
        manager.add ( new Separator () );
        manager.add ( this.propertiesAction );
    }

    private void fillLocalToolBar ( final IToolBarManager manager )
    {
        //manager.add(connectAction);
        //manager.add(new Separator());
        //drillDownAdapter.addNavigationActions ( manager );
    }

    private void makeActions ()
    {
        // Connect Action

        /*
        connectAction = new Action() {
            public void run() {
                performConnect();
            }
        };
        connectAction.setText("Connect");
        connectAction.setToolTipText("Establish connection to hive");
        connectAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
                getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));
        */
        this.connectAction = new ConnectStorageAction ();
        this.propertiesAction = new PropertyDialogAction ( new SameShellProvider ( getViewSite ().getShell () ), this._viewer );
        this._viewer.addSelectionChangedListener ( new ISelectionChangedListener () {

            public void selectionChanged ( final SelectionChangedEvent event )
            {
                final IStructuredSelection ss = (IStructuredSelection)event.getSelection ();
                if ( ss.size () == 1 )
                {
                    StorageView.this.propertiesAction.setEnabled ( true );
                }
                else
                {
                    StorageView.this.propertiesAction.setEnabled ( false );
                }
            }
        } );
    }

    private void hookDoubleClickAction ()
    {
        this._viewer.addDoubleClickListener ( new IDoubleClickListener () {
            public void doubleClick ( final DoubleClickEvent event )
            {
                StorageView.this.connectAction.selectionChanged ( null, event.getSelection () );
                StorageView.this.connectAction.run ( null );
            }
        } );
    }

    /**
     * Passing the focus request to the viewer's control.
     */
    @Override
    public void setFocus ()
    {
        this._viewer.getControl ().setFocus ();
    }

    synchronized private void unregisterAllConnections ()
    {
        for ( final StorageConnection connection : this._repository.getConnections () )
        {
            connection.deleteObserver ( this );
        }
    }

    synchronized private void registerAllConnections ()
    {
        unregisterAllConnections ();

        for ( final StorageConnection connection : this._repository.getConnections () )
        {
            connection.addObserver ( this );
        }
    }

    @Override
    public Object getAdapter ( final Class adapter )
    {
        _log.debug ( "getAdapter: " + adapter );
        if ( adapter.equals ( org.eclipse.ui.views.properties.IPropertySheetPage.class ) )
        {
            final PropertySheetPage psd = new PropertySheetPage ();
            return psd;
        }
        else
        {
            return super.getAdapter ( adapter );
        }
    }
}