/*******************************************************************************
 * PPA - Partial Program Analysis for Java
 * Copyright (C) 2008 Barthelemy Dagenais
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 3 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this library. If not, see 
 * <http://www.gnu.org/licenses/lgpl-3.0.txt>
 *******************************************************************************/
package org.eclipse.jdt.core.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.internal.compiler.lookup.PPATypeBindingOptions;

import ca.mcgill.cs.swevo.ppa.PPAASTUtil;

/**
 * <p>This visitor determines the kind of ambiguous syntax construct (like field vs. type).</p>
 * 
 * @author Barthelemy Dagenais
 *
 */
public class SyntaxDisambiguation extends ASTVisitor {

	private PPADefaultBindingResolver ppaResolver;

	private Set<ASTNode> ambNodes = new HashSet<ASTNode>();

	private Map<ASTNode, Map<String, List<Name>>> ambiguousFieldsTypes = new HashMap<ASTNode, Map<String, List<Name>>>();

	private Set<Name> ambiguousMethods = new HashSet<Name>();

	private Set<Name> ambiguousParameterizedTypes = new HashSet<Name>();
	
	private Map<ASTNode, Map<String, AmbiguousFQNNodes>> ambiguousFQNs = new HashMap<ASTNode, Map<String, AmbiguousFQNNodes>>();

	public SyntaxDisambiguation(PPADefaultBindingResolver ppaResolver) {
		super();
		this.ppaResolver = ppaResolver;
	}

	private void addAmbiguousFieldType(SimpleName node) {
		// TODO Identifying the correct container is MIGHTY important
		ASTNode container = PPAASTUtil.getFieldContainer(node, true, false);
		Map<String, List<Name>> ambiguousNodes = ambiguousFieldsTypes.get(container);
		if (ambiguousNodes == null) {
			ambiguousNodes = new HashMap<String, List<Name>>();
			ambiguousFieldsTypes.put(container, ambiguousNodes);
		}

		String nameKey = PPAASTUtil.getQualifierPlusName(node);
		List<Name> nodes = ambiguousNodes.get(nameKey);
		if (nodes == null) {
			nodes = new ArrayList<Name>();
			ambiguousNodes.put(nameKey, nodes);
		}
		nodes.add(node);
		this.ambNodes.add(node);
	}

	// @Override
	// public void endVisit(ConstructorInvocation node) {
	// if (ppaResolver.isProblematicBinding(node)) {
	// ppaResolver.fixMethodDeclaration(node);
	// }
	// }

	@Override
	public void endVisit(MethodDeclaration node) {
		if (ppaResolver.isProblematicBinding(node)) {
			ppaResolver.fixMethodDeclaration(node);
		}
	}

	@Override
	public void endVisit(SimpleType node) {
		if (ppaResolver.isProblematicBinding(node)) {
			ppaResolver.fixSimpleType(node);
		}
	}

	@Override
	public void endVisit(ClassInstanceCreation node) {
		if (ppaResolver.isProblematicBinding(node)) {
			ppaResolver.fixClassInstanceCreation(node);
			this.ambNodes.add(node);
		}
		
	}
	
	@Override
	public void endVisit(ConstructorInvocation node) {
		if (ppaResolver.isProblematicBinding(node)) {
			ppaResolver.fixConstructorInvocation(node);
		}
	}

	@Override
	public void endVisit(SimpleName node) {
		if (ppaResolver.isProblematicBinding(node) || PPAASTUtil.isUnsafeMethod(node)) {
			if (ppaResolver.isParameterizedSingleTypeRef(node)) {
				ambiguousParameterizedTypes.add(node);
			} else if (ppaResolver.isSingleTypeRef(node)) {
				ppaResolver.fixSingleTypeRef(node);
			} else if (ppaResolver.isQualifiedTypeReference(node)) {
				ppaResolver.fixQualifiedTypeReference(node);
			} else if (ppaResolver.isLocalDeclaration(node)) {
				ppaResolver.fixLocal(node);
			} else if (ppaResolver.isSimpleNameRef(node)
					|| ppaResolver.isQualifiedNameReference(node)
					|| ppaResolver.isFieldReference(node)) {
				addAmbiguousFieldType(node);
			} else if (ppaResolver.isMessageSend(node)) {
				ambiguousMethods.add(node);
			} else if (ppaResolver.isMemberValuePair(node)) {
				ppaResolver.fixMemberValuePair(node);
			}
		} else {
			ASTNode parent = node.getParent();
			if ((ppaResolver.isSimpleNameRef(node) || ppaResolver.isQualifiedNameReference(node) || ppaResolver
					.isFieldReference(node))
					&& parent instanceof QualifiedName) {
				if (PPABindingsUtil.getSafetyValue(node.resolveTypeBinding()) < PPABindingsUtil.FULL_TYPE) {

					ASTNode container = PPAASTUtil.getFieldContainer(node, true, false);
					IBinding binding = node.resolveBinding();
					if (binding != null && binding instanceof IVariableBinding) {
						addToAmbiguousFQNs(container, node.getFullyQualifiedName(), node,
								(IVariableBinding) binding);
					}
				}
			}
		}

		super.endVisit(node);
	}

	public void postProcess() {
		for (ASTNode container : ambiguousFieldsTypes.keySet()) {
			Map<String, List<Name>> ambiguousNodes = ambiguousFieldsTypes.get(container);
			for (String name : ambiguousNodes.keySet()) {
				List<Name> nodes = ambiguousNodes.get(name);
				if (!PPAASTUtil.isIndicationOfField(nodes)) {
					addTypeBinding(container, name, nodes);
				} else {
					addFieldBinding(container, name, nodes);
				}
			}
		}

		processAmbiguousFQN();

		// Type parameters must be fixed before fixing the container type,
		// hence the reason why they are processed at the end.
		for (Name name : ambiguousParameterizedTypes) {
			ppaResolver.fixParameterizedSingleTypeRef(name);
		}
		
		for (Name name : ambiguousMethods) {
			ppaResolver.fixMessageSend(name);
			this.ambNodes.add(name);
		}
		
		
	}

	private void processAmbiguousFQN() {
		for (ASTNode container : ambiguousFQNs.keySet()) {
			Map<String, AmbiguousFQNNodes> map = ambiguousFQNs.get(container);
			List<Map.Entry<String, AmbiguousFQNNodes>> list = getOrderedListFromMap(map);
			processOrderedFQNList(list);
		}
	}

	private void processOrderedFQNList(List<Entry<String, AmbiguousFQNNodes>> list) {
		String current = "-1";
		// String previous = "-1";
		int size = list.size();
		Map.Entry<String, AmbiguousFQNNodes> currentEntry;
		IVariableBinding previousBinding = null;
		PPATypeRegistry typeRegistry = ppaResolver.getTypeRegistry();
		boolean lastOfItsKind = false;
		Map<String, IVariableBinding> previousBindings = new HashMap<String, IVariableBinding>();
		for (int i = 0; i < size; i++) {

			currentEntry = list.get(i);
			// previous = current;
			current = currentEntry.getKey();
			// CompilationUnit cu =
			// (CompilationUnit)PPAUtil.getSpecificParentType
			// (currentEntry.getValue
			// ().getNodes().get(0),ASTNode.COMPILATION_UNIT);

			// TODO: BUG! In fact, the sort is not good! Arg!
			// z1.z2 = z1.z3.z4
			// z1
			// then z2
			// then z3 (previous is z1.z2);
			// if (isNewKind(current, previous)) {
			// previousBinding = null;
			// }
			previousBinding = getPreviousBinding(current, previousBindings);
			lastOfItsKind = lastOfItsKind(list, i);

			if (lastOfItsKind && !currentEntry.getValue().isField() && previousBinding == null) {
				for (ASTNode node : currentEntry.getValue().getNodes()) {
					ppaResolver.fixFQNType(node, current);
				}
			} else if (previousBinding != null) {
				// IVariableBinding entryBinding =
				// currentEntry.getValue().getFieldBinding();
				// ITypeBinding containerType = previousBinding.getType();
				// ITypeBinding fieldType = (entryBinding == null ||
				// entryBinding.getType() == null) ? typeRegistry
				// .getUnknownBinding(ppaResolver)
				// : entryBinding.getType();
				IVariableBinding newBinding = typeRegistry.getUnknownFieldBinding(PPABindingsUtil
						.getSimpleName(current).toCharArray(), ppaResolver);

				for (ASTNode node : currentEntry.getValue().getNodes()) {
					ppaResolver.fixFQNField(node, newBinding, current, lastOfItsKind);
				}

				previousBindings.put(currentEntry.getKey(), newBinding);
			} else if (currentEntry.getValue().isField()) {
				// IVariableBinding entryBinding =
				// currentEntry.getValue().getFieldBinding();
				// ITypeBinding containerType =
				// entryBinding.getDeclaringClass();
				//
				// if (containerType == null ||
				// PPAUtil.isUnknownType(containerType)) {
				// containerType = typeRegistry.getTypeBinding(cu,
				// PPAUtil.getPackage(current),
				// ppaResolver);
				// }
				// ITypeBinding fieldType = entryBinding.getType() == null ?
				// typeRegistry
				// .getUnknownBinding(ppaResolver) : entryBinding.getType();
				IVariableBinding newBinding = typeRegistry.getUnknownFieldBinding(PPABindingsUtil
						.getSimpleName(current).toCharArray(), ppaResolver);

				for (ASTNode node : currentEntry.getValue().getNodes()) {
					ppaResolver.fixFQNField(node, newBinding, current, lastOfItsKind);
				}
				previousBindings.put(currentEntry.getKey(), newBinding);
			}

		}
	}

	private IVariableBinding getPreviousBinding(String current,
			Map<String, IVariableBinding> previousBindings) {
		IVariableBinding varBinding = null;
		int index = current.lastIndexOf('.');
		if (index != -1) {
			String parent = current.substring(0, index);
			varBinding = previousBindings.get(parent);
		}
		return varBinding;
	}

	/**
	 * <p>
	 * If the previous FQN is not the prefix of the current FQN, the current FQN is a new kind.
	 * </p>
	 * 
	 * @param current
	 * @param previous
	 * @return
	 */
	// private boolean isNewKind(String current, String previous) {
	// return !current.startsWith(previous);
	// }
	/**
	 * <p>
	 * If the current FQN is not the prefix of the next one, it is the last of its kind.
	 * </p>
	 * 
	 * @param list
	 * @param currentFQN
	 * @param currentIndex
	 * @return
	 */
	private boolean lastOfItsKind(List<Entry<String, AmbiguousFQNNodes>> list, int currentIndex) {
		boolean lastOfItsKind = true;

		if (list.size() > currentIndex + 1) {
			lastOfItsKind = !list.get(currentIndex + 1).getKey().startsWith(
					list.get(currentIndex).getKey());
		}

		return lastOfItsKind;
	}

	private List<Map.Entry<String, AmbiguousFQNNodes>> getOrderedListFromMap(
			Map<String, AmbiguousFQNNodes> map) {
		List<Map.Entry<String, AmbiguousFQNNodes>> list = new ArrayList<Entry<String, AmbiguousFQNNodes>>(
				map.entrySet());
		Collections.sort(list, new AmbiguousFQNsComparator());
		return list;
	}

	private void addFieldBinding(ASTNode container, String name, List<Name> nodes) {
		PPATypeRegistry typeRegistry = ppaResolver.getTypeRegistry();
		IVariableBinding fieldBinding = typeRegistry.getUnknownFieldBinding(name.toCharArray(),
				ppaResolver);
		;

		// ITypeBinding probableContainerType = PPAUtil
		// .getFirstFieldContainerMissingSuperType(container);

		for (Name node : nodes) {
			if (node.getParent() instanceof QualifiedName) {
				addToAmbiguousFQNs(container, name, node, fieldBinding);
			} else {
				ppaResolver.fixFieldBinding(node, fieldBinding);
			}
		}
	}

	private void addTypeBinding(ASTNode container, String name, List<Name> nodes) {
		CompilationUnit cu = (CompilationUnit) PPAASTUtil.getSpecificParentType(nodes.get(0),
				ASTNode.COMPILATION_UNIT);
		PPATypeRegistry typeRegistry = ppaResolver.getTypeRegistry();
		PPATypeBindingOptions options = new PPATypeBindingOptions();
		if (nodes.size() > 0) {
			options = PPATypeBindingOptions.parseOptions(nodes.get(0));
		}
		ITypeBinding tBinding = typeRegistry.getTypeBinding(cu, name, ppaResolver, false, options);
		for (Name node : nodes) {
			if (node.getParent() instanceof QualifiedName) {
				addToAmbiguousFQNs(container, name, node, null);
			} else {
				ppaResolver.fixTypeBinding(node, tBinding);
			}
		}
	}

	private void addToAmbiguousFQNs(ASTNode container, String name, Name node,
			IVariableBinding fieldBinding) {
		ASTNode newContainer = PPAASTUtil.getFieldContainer(container, false, true);
		Map<String, AmbiguousFQNNodes> map = ambiguousFQNs.get(newContainer);
		if (map == null) {
			map = new HashMap<String, AmbiguousFQNNodes>();
			ambiguousFQNs.put(newContainer, map);
		}

		AmbiguousFQNNodes nodes = map.get(name);
		if (nodes == null) {
			nodes = new AmbiguousFQNNodes();
			map.put(name, nodes);
		}

		if (fieldBinding != null) {
			// Only overwrite if true
			nodes.setFieldBinding(fieldBinding);
		}

		nodes.getNodes().add(node);

	}

	public Set<ASTNode> getAmbiguousNodes() {
		return ambNodes;
	}

}

class AmbiguousFQNNodes {

	private List<ASTNode> nodes = new ArrayList<ASTNode>();

	private IVariableBinding fieldBinding;

	public boolean isField() {
		return fieldBinding != null;
	}

	public List<ASTNode> getNodes() {
		return nodes;
	}

	public void setNodes(List<ASTNode> nodes) {
		this.nodes = nodes;
	}

	public IVariableBinding getFieldBinding() {
		return fieldBinding;
	}

	public void setFieldBinding(IVariableBinding fieldBinding) {
		if (this.fieldBinding == null) {
			this.fieldBinding = fieldBinding;
		}
	}

}

class AmbiguousFQNsComparator implements Comparator<Map.Entry<String, AmbiguousFQNNodes>> {

	public int compare(Entry<String, AmbiguousFQNNodes> o1, Entry<String, AmbiguousFQNNodes> o2) {
		return o1.getKey().compareTo(o2.getKey());
	}

}
