package es.ynel.mongui.zk.window;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.springframework.util.StringUtils;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.SelectEvent;
import org.zkoss.zk.ui.select.SelectorComposer;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zul.DefaultTreeModel;
import org.zkoss.zul.DefaultTreeNode;
import org.zkoss.zul.ListModelList;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Tree;
import org.zkoss.zul.Treecell;
import org.zkoss.zul.Treeitem;
import org.zkoss.zul.TreeitemRenderer;
import org.zkoss.zul.Treerow;
import org.zkoss.zul.Window;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

@VariableResolver(org.zkoss.zkplus.spring.DelegatingVariableResolver.class)
public class DatabaseWnd extends SelectorComposer<Window>{

	private static final long serialVersionUID = 956842732052856692L;
	
	@WireVariable
	private MongoClient mongoClient;
	
	@Wire
	private Listbox databases;
	
	@Wire
	private Listbox collections;
	
	@Wire
	private Tree data;

	private DB db;

	private DBCollection dbCollection;

	@Override
	public void doAfterCompose(Window comp) throws Exception
	{
		super.doAfterCompose(comp);
		List<String> databaseNames = mongoClient.getDatabaseNames();
		databaseNames.add(0, null);
		databases.setModel(new ListModelList<String>(databaseNames));
		
		data.setItemRenderer(new ElementTreeitemRenderer());
	}
	
	@Listen(Events.ON_SELECT + "=#databases")
	public void onSelectDatabase(SelectEvent<Listitem, String> event)
	{
		String database = event.getSelectedObjects().iterator().next();
		db = mongoClient.getDB(database);
		Set<String> setCollectionNames = db.getCollectionNames();
		List<String> collectionNames = new ArrayList<String>(setCollectionNames);
		collectionNames.add(0, null);
		collections.setModel(new ListModelList<String>(collectionNames));
		dbCollection = null;
		refreshTree();
	}
	
	@Listen(Events.ON_SELECT + "=#collections")
	public void onSelectCollection(SelectEvent<Listitem, String> event)
	{
		String collection = event.getSelectedObjects().iterator().next();
		
		dbCollection = StringUtils.isEmpty(collection) ? null : db.getCollection(collection);
		refreshTree();
	}

	private void refreshTree()
	{
		if (dbCollection == null)
		{
			data.setModel(null);
			return;
		}
		
		DBCursor dbCursor = null;
		
		try
		{
			dbCursor = dbCollection.find();
		
			ElementTreeNode root = new ElementTreeNode(null, null, dbCursor.count());
			
			int i = 0;
			Iterator<DBObject> it = dbCursor.iterator();
			while (it.hasNext())
			{
				DBObject dbObject = it.next();
				Object id = dbObject.get("_id");
				
				root.add(new ElementTreeNode(dbObject, (id != null ? id.toString() : String.valueOf(i)), dbObject.keySet().size()));
				i++;
			}
			
			data.setModel(new DefaultTreeModel<DBObject>(root));
		}
		finally
		{
			if (dbCursor != null)
			{
				dbCursor.close();
			}
		}
	}
	
	@Listen(Events.ON_CLICK + "=#delete")
	public void onClickDelete()
	{
		if (data.getSelectedCount() == 0)
		{
			return;
		}
		
		ElementTreeNode node = data.getSelectedItem().getValue();
		if (node.parent)
		{
			dbCollection.remove((DBObject) node.getData());
			refreshTree();
			return;
		}
		
		String key = "";
		
		if (node.value instanceof String[])
		{
			key = ((String[])node.value)[0];
		}
		else
		{
			key = node.name;
		}
		
		dbCollection.update(node.getData(), new BasicDBObject("$unset", new BasicDBObject(key, 1)));
		refreshTree();
	}
	
	private class ElementTreeNode extends DefaultTreeNode<DBObject> {

		private int children;
		private String name;
		private boolean parent;
		private Object value;

		public ElementTreeNode(DBObject parent, String name, int children)
		{
			super(parent, new LinkedList());
			this.children = children;
			this.name = name;
			this.parent = true;
		}
		
		public ElementTreeNode(DBObject parent, String name, Object value, int children)
		{
			super(parent, new LinkedList());
			this.children = children;
			this.name = name;
			this.value = value;
			this.parent = false;
		}

		@Override
		public boolean isLeaf()
		{
			return children == 0;
		}
	}
	
	private class ElementTreeitemRenderer implements TreeitemRenderer<ElementTreeNode> 
	{

		@Override
		public void render(Treeitem item, ElementTreeNode node, int index) throws Exception 
		{
			Treerow row = new Treerow();
			item.appendChild(row);
			
			if (node.parent)
			{
				Treecell cell = new Treecell();
				cell.setLabel(node.name);
				cell.setSpan(2);
				row.appendChild(cell);
			}
			else
			{
				if (node.value instanceof String[])
				{
					Treecell cell = new Treecell();
					cell.setLabel(((String[])node.value)[0]);
					row.appendChild(cell);
					
					Treecell cell2 = new Treecell();
					cell2.setLabel(((String[])node.value)[1]);
					row.appendChild(cell2);
				}
				else
				{
					Treecell cell = new Treecell();
					cell.setLabel(node.name);
					cell.setSpan(2);
					row.appendChild(cell);
				}
			}
			
			item.setValue(node);
			
			if (node.parent || (node.value != null && node.value instanceof DBObject))
			{
				DBObject dbObject = node.parent ? node.getData() : (DBObject) node.value;
				Iterator<String> it = dbObject.keySet().iterator();
				while (it.hasNext())
				{
					String key = it.next();
					if (key.equals("_id") && node.parent)
					{
						continue;
					}
					
					Object value = dbObject.get(key);
					
					if (value instanceof DBObject)
					{
						node.add(new ElementTreeNode(node.getData(), key, value, (value instanceof DBObject ? ((DBObject) value).keySet().size() : 0)));
					}
					else
					{
						node.add(new ElementTreeNode(node.getData(), null, new String[]{key, value == null ? "[null]" : value.toString()}, 0));
					}
				}
			}
		}
	}
}
