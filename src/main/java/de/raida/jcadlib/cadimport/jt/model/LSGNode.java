//################################################################################
//	The MIT License
//
//	Copyright (c) 2014 Johannes Raida
//
//	Permission is hereby granted, free of charge, to any person obtaining a copy
//	of this software and associated documentation files (the "Software"), to deal
//	in the Software without restriction, including without limitation the rights
//	to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
//	copies of the Software, and to permit persons to whom the Software is
//	furnished to do so, subject to the following conditions:
//
//	The above copyright notice and this permission notice shall be included in
//	all copies or substantial portions of the Software.
//
//	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//	FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//	AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//	LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//	OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
//	THE SOFTWARE.
//################################################################################

package de.raida.jcadlib.cadimport.jt.model;

import de.raida.jcadlib.cadimport.jt.model.lsg.InstanceNodeElement;
import de.raida.jcadlib.cadimport.jt.model.lsg.MetaDataNodeElement;
import de.raida.jcadlib.cadimport.jt.model.lsg.PartNodeElement;
import de.raida.jcadlib.cadimport.jt.model.lsg.PartitionNodeElement;
import de.raida.jcadlib.cadimport.jt.model.property.LateLoadedPropertyAtomElement;
import de.raida.jcadlib.cadimport.jt.model.property.StringPropertyAtomElement;

import java.util.*;

/**
 * Interface for LSG nodes.
 * <br>(c) 2014 by <a href="mailto:j.raida@gmx.net">Johannes Raida</a>
 * @author  <a href="mailto:j.raida@gmx.net">Johannes Raida</a>
 * @version 1.0
 */
public abstract class LSGNode implements JTNode {
	/** Parent node */
	private LSGNode _parentNode;

	/** List of child nodes */
	private List<LSGNode> _childNodes;

	/** List of attribute nodes */
	private List<JTNode> _attributeNodes;

	/** List of property nodes */
	private Map<JTNode, JTNode> _propertyNodes;

	private Map<String, Object> _llProperties = new HashMap<>();

	/**
	 * Constructor.
	 */
	public LSGNode(){
		_childNodes = new ArrayList<>();
		_attributeNodes = new ArrayList<>();
		_propertyNodes = new HashMap<>();
	}

	/**
	 * Sets the parent node.
	 * @param parentNode Parent node
	 */
	public void setParentLSGNode(LSGNode parentNode){
		_parentNode = parentNode;
	}

	/**
	 * Returns the parent node.
	 * @return Parent node
	 */
	public LSGNode getParentLSGNode(){
		return _parentNode;
	}

	/**
	 * Adds a child node.
	 * @param childNode Child node
	 */
	public void addChildLSGNode(LSGNode childNode){
		_childNodes.add(childNode);
	}

	/**
	 * Returns the child nodes.
	 * @return Child nodes
	 */
	public List<LSGNode> getChildLSGNodes(){
		return _childNodes;
	}

	/**
	 * Adds an attribute node
	 * @param jtNode Attribute node
	 */
	public void addAttributeNode(JTNode jtNode){
		if(!_attributeNodes.contains(jtNode)){
			_attributeNodes.add(jtNode);
		}
	}

	/**
	 * Returns the attribute nodes.
	 * @return Attribute nodes
	 */
	public List<JTNode> getAttributeNodes(){
		return _attributeNodes;
	}

	/**
	 * Sets the attribute nodes.
	 * @param attributeNodes Attribute nodes
	 */
	public void setAttributeNodes(List<JTNode> attributeNodes){
		_attributeNodes = attributeNodes;
	}

	/**
	 * Adds a property node
	 * @param keyNode   Property node (key)
	 * @param valueNode Property node (value)
	 */
	public void addPropertyNode(JTNode keyNode, JTNode valueNode){
		if(!_propertyNodes.containsKey(keyNode)){
			_propertyNodes.put(keyNode, valueNode);
		}
	}

	/**
	 * Returns the property nodes.
	 * @return Property nodes
	 */
	public Map<JTNode, JTNode> getPropertyNodes(){
		return _propertyNodes;
	}

	public void setLLProperties(Map<String, Object> map) {
		this._llProperties = map;
	}

	public Map<String, Object> getLLProperties() {
		return this._llProperties;
	}

	public Optional<LateLoadedPropertyAtomElement> queryLLMetadataElement(){
		return queryPropValue("JT_LLPROP_METADATA")
				.map(v -> (v instanceof LateLoadedPropertyAtomElement)
					? (LateLoadedPropertyAtomElement)v : null);
	}

	/**
	 * Sets the property nodes.
	 * @param propertyNodes Property nodes
	 */
	public void setPropertyNodes(Map<JTNode, JTNode> propertyNodes){
		_propertyNodes = propertyNodes;
	}

    /**
     * Returns the node name.
     * @return         Name of the node
     */
    public Optional<String> getName(){
        return this.get_JT_PROP_NAME().map(LSGNode::stripNameSuffix);
    }


    public Optional<String> get_JT_PROP_NAME(){
        return getPropValueAsString("JT_PROP_NAME");
    }

    public Optional<String> get_JT_PROP_MEASUREMENT_UNITS(){
        return getPropValueAsString("JT_PROP_MEASUREMENT_UNITS");
    }

    static public String stripNameSuffix(String rawName) {
        int lastDot = rawName.lastIndexOf('.');
        if ( lastDot > 0 ) {
            String suffix = rawName.substring(lastDot+1);
            if ( suffix.startsWith("part;")
                    || suffix.startsWith("asm;") ) {
                return rawName.substring(0,lastDot);
            }
        }
        return rawName;
    }

	/**
	 * Returns the node name.
	 * @param  key LSG Key
	 * @return         Value node
	 */
	public Optional<JTNode> queryPropValue(String key) {
		Map<JTNode, JTNode> propertyMap = this.getPropertyNodes();
		return propertyMap
				.entrySet()
				.stream()
				.filter(pair-> {
					if ( pair.getKey() instanceof StringPropertyAtomElement) {
						StringPropertyAtomElement strKey = (StringPropertyAtomElement) pair.getKey();
						return (strKey.getValue().equals(key));
					}
					return false;
				})
				.map(Map.Entry::getValue)
				.findFirst();
	}

	/**
     * Returns the node name.
	 * @param  key LSG Key
	 * @return         Value
     */
    public Optional<String> getPropValueAsString(String key) {
		return queryPropValue(key)
				.map(n->((StringPropertyAtomElement)n).getValue());
    }

	public boolean isJtPartition() {
	    return ( this instanceof PartitionNodeElement );
    }

    public boolean isJtInstance() {
        return ( this instanceof InstanceNodeElement);
    }

    public boolean isJtMDN() {
        return ( this instanceof MetaDataNodeElement);
    }

    public boolean isJtPart() {
        return ( this instanceof PartNodeElement);
    }

    /**
	 * Returns the child node object IDs.
	 * @return Child node object IDs
	 */
	public abstract int[] getChildNodeObjectIDs();

	/**
	 * Returns the attribute object IDs.
	 * @return Attribute object IDs
	 */
	public abstract int[] getAttributeObjectIDs();

	/**
	 * Returns a copy of the current class.
	 * @param  lsgNode Parent node
	 * @return         Copy of the current class
	 */
	public abstract LSGNode copy(LSGNode lsgNode);
}
