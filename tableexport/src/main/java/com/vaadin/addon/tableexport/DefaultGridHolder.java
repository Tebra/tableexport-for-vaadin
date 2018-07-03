package com.vaadin.addon.tableexport;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import com.vaadin.data.HasHierarchicalDataProvider;
import com.vaadin.data.ValueProvider;
import com.vaadin.data.provider.Query;
import com.vaadin.server.SerializableFunction;
import com.vaadin.shared.ui.grid.renderers.HtmlRendererState;
import com.vaadin.ui.Grid;
import com.vaadin.ui.Grid.Column;
import com.vaadin.ui.UI;
import com.vaadin.ui.renderers.Renderer;

public class DefaultGridHolder implements TableHolder {

    protected short defaultAlignment = HorizontalAlignment.LEFT.getCode();

    private boolean hierarchical = false;

    protected Grid<?> heldGrid;
    private List<Object> propIds;

    public DefaultGridHolder(Grid<?> grid) {
        this.heldGrid = grid;
        this.propIds = heldGrid.getColumns().stream().map(Column::getId).collect(Collectors.toList());
        setHierarchical(grid instanceof HasHierarchicalDataProvider);
    }

    @Override
    public List<Object> getPropIds() {
        return propIds;
    }

    @Override
    public boolean isHierarchical() {
        return hierarchical;
    }

    @Override
    final public void setHierarchical(boolean hierarchical) {
        this.hierarchical = hierarchical;
    }

    @Override
    public Short getCellAlignment(Object propId) {
        if (null == heldGrid) {
            return defaultAlignment;
        }
        Renderer<?> renderer = getRenderer(propId);
        if (renderer != null) {
            if (ExcelExport.isNumeric(renderer.getPresentationType())) {
            	return HorizontalAlignment.RIGHT.getCode();
            }
        }
        return defaultAlignment;
    }

    @Override
    public boolean isGeneratedColumn(final Object propId) throws IllegalArgumentException {
        return false;
    }

    @Override
    public Class<?> getPropertyTypeForGeneratedColumn(final Object propId) throws IllegalArgumentException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isExportableFormattedProperty() {
        return false;
    }

    @Override
    public boolean isColumnCollapsed(Object propertyId) {
        if (null == heldGrid) {
            return false;
        }
        return heldGrid.getColumn((String) propertyId).isHidden();
    }

    @Override
    public UI getUI() {
        if (null != heldGrid) {
            return heldGrid.getUI();
        }
        return UI.getCurrent();
    }

    @Override
    public String getColumnHeader(Object propertyId) {
        if (null != heldGrid) {
            Column<?,?> c = getColumn(propertyId);
            return c.getCaption();
        } else {
            return propertyId.toString();
        }
    }

    protected Column<?,?> getColumn(Object propId) {
    	return heldGrid.getColumn((String) propId);
    }

    protected Renderer<?> getRenderer(Object propId) {
    	Column<?,?> column = getColumn(propId);
    	if (column != null) {
    		return column.getRenderer();
    	}
    	return null;
    }

    @Override
    public Class<?> getPropertyType(Object propId) {
        Renderer<?> renderer = getRenderer(propId);
        if (renderer != null) {
            return renderer.getPresentationType();
        } else {
            return String.class;
        }
    }

    @Override
    public Object getPropertyValue(Object itemId, Object propId, boolean useTableFormatPropertyValue) {
        final Field getter;
        Column<?, ?> column = getColumn(propId);

        /* Workaround for Vaadin 8.x private presentationProvider getter -> We have to access it via reflection -- this could be dangerous. FIXME Vaadin API Change */
        try {
            getter = Column.class.getDeclaredField("presentationProvider");
            getter.setAccessible(true);

            SerializableFunction presentationProvider = (ValueProvider) getter.get(column);
            SerializableFunction valueProvider = column.getValueProvider();
            Object itemIdappliedWithValueProvider = valueProvider.apply(itemId);

            /* The second part in the if statement is because we don't want the HTML of some checkboxes / icons to be seen in the exported files - ignore presentation providers with HTML state */
            if (presentationProvider == null || (getRenderer(propId) != null && getRenderer(propId).getStateType().equals(HtmlRendererState.class))) {
                return itemIdappliedWithValueProvider;
            }

            return presentationProvider.apply(itemIdappliedWithValueProvider);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Error retrieving presentation provider from grid renderer! - table export library error: "  + e.getMessage());
        }
    }

    @Override
    public Collection<?> getChildren(Object rootItemId) {
    	if (heldGrid instanceof HasHierarchicalDataProvider) {
    		return ((HasHierarchicalDataProvider) heldGrid).getTreeData().getChildren(rootItemId);
        } else {
        	return Collections.emptyList();
        }
    }
    
    @Override
    public Collection<?> getItemIds() {
    	return heldGrid.getDataProvider().fetch(new Query<>()).collect(Collectors.toList());
    }

    @Override
    public Collection<?> getRootItemIds() {
    	if (heldGrid instanceof HasHierarchicalDataProvider) {
    		return ((HasHierarchicalDataProvider) heldGrid).getTreeData().getRootItems();
    	} else {
    		return getItemIds();
    	}
    }

}
