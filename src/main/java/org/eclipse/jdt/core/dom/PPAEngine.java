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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.DefaultBindingResolver;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.mcgill.cs.swevo.ppa.PPAIndex;
import ca.mcgill.cs.swevo.ppa.PPAIndexer;
import ca.mcgill.cs.swevo.ppa.PPALoggerUtil;
import ca.mcgill.cs.swevo.ppa.PPAOptions;
import ca.mcgill.cs.swevo.ppa.TypeFact;
import ca.mcgill.cs.swevo.ppa.TypeFactMerger;

public class PPAEngine {

	// private final static PPAEngine instance = new PPAEngine();

	private List<TypeFact> worklist = new LinkedList<TypeFact>();

	private Map<PPAIndex, List<ASTNode>> unsafeNodes = new HashMap<PPAIndex, List<ASTNode>>();

	private PPAIndexer indexer;

	private TypeFactMerger merger = new TypeFactMerger();

	private Set<ASTNode> ambiguousNodes = new HashSet<ASTNode>();

	private List<ASTNode> unitsToProcess = new ArrayList<ASTNode>();

	private PPATypeRegistry typeRegistry;

	private boolean allowMemberInference = true;

	private boolean allowCollectiveMode = false;

	private boolean allowTypeInferenceMode = true;

	private boolean allowMethodBindingMode = true;

	private int maxMISize = -1;

	private int MAX_TURN = 1000000;

	private TypeFact currentFact;

	private boolean isInMethodBindingPass = false;

	private final Logger logger = PPALoggerUtil.getLogger(PPAEngine.class);

	public PPAEngine(PPATypeRegistry typeRegistry, PPAOptions options) {
		indexer = new PPAIndexer(this);
		this.typeRegistry = typeRegistry;
		this.setAllowMemberInference(options.isAllowMemberInference());
		this.setAllowCollectiveMode(options.isAllowCollectiveMode());
		this.setAllowTypeInferenceMode(options.isAllowTypeInferenceMode());
		this.setAllowMethodBindingMode(options.isAllowMethodBindingMode());
		this.setMaxMISize(options.getMaxMISize());
	}

	// public static PPAEngine getInstance() {
	// return instance;
	// }

	public void addAmbiguousNodes(Set<ASTNode> nodes) {
		ambiguousNodes.addAll(nodes);
	}

	public void addUnitToProcess(ASTNode node) {
		unitsToProcess.add(node);
	}

	public boolean allowCollectiveMode() {
		return allowCollectiveMode;
	}

	public boolean allowMemberInference() {
		return allowMemberInference;
	}

	public boolean allowMethodBindingMode() {
		return allowMethodBindingMode;
	}

	public boolean allowTypeInferenceMode() {
		return allowTypeInferenceMode;
	}

	public void doPPA() {
		if (allowCollectiveMode) {
			seedPass();
			if (allowTypeInferenceMode) {
				typeInferencePass();
				if (allowMethodBindingMode) {
					methodBindingPass();
				}
			}
			postPass();
		} else {
			for (ASTNode node : unitsToProcess) {
				seedPass(node);
				if (allowTypeInferenceMode) {
					typeInferencePass();
					if (allowMethodBindingMode) {
						methodBindingPass();
					}
				}
				postPass(node);
				reset();
			}
		}
	}

	private List<MethodInvocation> getAmbiguousMethodInvocations() {
		List<MethodInvocation> mis = new ArrayList<MethodInvocation>();
		for (ASTNode node : ambiguousNodes) {
			if (node instanceof SimpleName) {
				SimpleName sName = (SimpleName) node;
				ASTNode parent = sName.getParent();
				if (parent instanceof MethodInvocation) {
					MethodInvocation mi = (MethodInvocation) parent;
					if (sName == mi.getName()) {
						mis.add(mi);
					}
				}
			}
		}
		return mis;
	}

	public Set<ASTNode> getAmbiguousNodes() {
		return Collections.unmodifiableSet(ambiguousNodes);
	}

	public int getMaxMISize() {
		return maxMISize;
	}

	public PPATypeRegistry getRegistry() {
		return typeRegistry;
	}

	public List<ASTNode> getUnitsToProcess() {
		return unitsToProcess;
	}

	// For debugging purpose only
	public List<TypeFact> getWorklist() {
		return worklist;
	}

	public boolean isInMethodBindingPass() {
		return isInMethodBindingPass;
	}

	private boolean isSecondaryIndex(PPAIndex index, List<PPAIndex> secondaryIndexes) {
		boolean found = false;

		for (PPAIndex tempIndex : secondaryIndexes) {
			if (tempIndex.equals(index)) {
				found = true;
				break;
			}
		}

		return found;
	}

	public void methodBindingPass() {
		isInMethodBindingPass = true;
		List<MethodInvocation> mis = getAmbiguousMethodInvocations();
		int size = mis.size();
		if (maxMISize == -1 || mis.size() < maxMISize) {
			for (MethodInvocation mi : mis) {
				IMethodBinding currentBinding = mi.resolveMethodBinding();
				PPABindingsUtil.fixMethod(mi.getName(), currentBinding.getReturnType(),
						typeRegistry,
						(PPADefaultBindingResolver) mi.getName().ast.getBindingResolver(), indexer,
						this, false, true);
				recomputeIndexes(unsafeNodes.get(indexer.getMainIndex(mi)));
			}
		} else {
			logger.info("Skipping method binding pass because too many ambiguous methods: " + size);
		}

		processWorklist();
		isInMethodBindingPass = false;
	}

	public void processWorklist() {
		int turn = 0;
		try {
			while (!worklist.isEmpty()) {
				turn++;

				if (turn > MAX_TURN) {
					assert false;
				}

				List<ASTNode> visitInSecond = new ArrayList<ASTNode>();
				currentFact = worklist.get(0);
				worklist.remove(0);

				// For debugging purpose.
				// System.out.println(currentFact);

				PPAIndex index = currentFact.getIndex();

				if (unsafeNodes.containsKey(index)) {
					for (ASTNode unsafeNode : unsafeNodes.get(index)) {
						PPAIndex mainIndex = indexer.getMainIndex(unsafeNode);
						List<PPAIndex> secondaryIndexes = indexer.getSecondaryIndexes(unsafeNode);
						if (mainIndex.equals(index)) {
							indexer.makeSafe(unsafeNode, currentFact);
						}

						// This is in case the type refer both to a main index AND a secondary
						// index.
						// For example, infinite fields a.a.a.a
						if (isSecondaryIndex(index, secondaryIndexes)) {
							visitInSecond.add(unsafeNode);
						}
					}
				} else {
					// We will try to avoid that case...
//					assert false;
				}

				for (ASTNode unsafeNode : visitInSecond) {
					// This is to enforce the fact that secondary indexes should NEVER use the new
					// type in the type fact, but should rather seek the actual fact in the primary
					// indexes.
					TypeFact tFact = (TypeFact) currentFact.clone();
					tFact.setNewType(null);
					indexer.makeSafeSecondary(unsafeNode, tFact);
				}

				recomputeIndexes(unsafeNodes.get(index));
			}

		} catch (Exception e) {
			logger.error("Error in PPAEngine", e);
			assert false;
		}
	}

	private void putUnsafeNode(PPAIndex index, ASTNode node) {
		List<ASTNode> nodes = unsafeNodes.get(index);
		if (nodes == null) {
			nodes = new ArrayList<ASTNode>();
			unsafeNodes.put(index, nodes);
		}

		if (!nodes.contains(node)) {
			nodes.add(node);
		}

	}

	public void recomputeIndexes(List<ASTNode> nodes) {
		if (nodes == null) {
			return;
		}
		for (ASTNode node : nodes) {
			reportUnsafe(node);
		}
	}

	public boolean reportTypeFact(TypeFact typeFact) {
		assert typeFact.getNewType() != null;

		if (typeFact.getIndex() == null) {
			// This is to prevent future problem.
			return false;
		} else if (!merger.isValuableTypeFact(typeFact)
				|| merger.similarTypeFacts(typeFact, currentFact)) {
			return false;
		}

		TypeFact similarFact = merger.findTypeFact(typeFact, worklist);
		if (similarFact == null) {
			worklist.add(typeFact);
		} else {
			worklist.remove(similarFact);
			TypeFact newFact = merger.merge(similarFact, typeFact);
			assert newFact.getNewType() != null;
			worklist.add(newFact);
		}

		return true;
	}

	public void reportUnsafe(ASTNode node) {
		for (PPAIndex index : indexer.getAllIndexes(node)) {
			putUnsafeNode(index, node);
		}
	}

	public void reset() {
		worklist.clear();
		unsafeNodes.clear();
		ambiguousNodes.clear();
		if (typeRegistry != null) {
			typeRegistry.clear();
		}
	}

	public void seedPass() {
		for (ASTNode node : unitsToProcess) {
			seedPass(node);
		}
	}

	public void seedPass(ASTNode node) {
		if (node.ast.getBindingResolver() instanceof DefaultBindingResolver) {
			PPADefaultBindingResolver resolver = new PPADefaultBindingResolver(
					(DefaultBindingResolver) node.ast.getBindingResolver(), typeRegistry);
			if (node instanceof CompilationUnit) {
				resolver.setCurrentCu((CompilationUnit) node);
			}
			node.ast.setBindingResolver(resolver);

			// Syntax Disambiguation
			SyntaxDisambiguation sDisambiguation = new SyntaxDisambiguation(resolver);
			node.accept(sDisambiguation);
			sDisambiguation.postProcess();
			addAmbiguousNodes(sDisambiguation.getAmbiguousNodes());

			if (allowMemberInference) {
				MemberInferencer mInferencer = new MemberInferencer(indexer, resolver, this);
				mInferencer.processMembers();
			}

			// Initial inference
			SeedVisitor sVisitor = new SeedVisitor(indexer, this);
			node.accept(sVisitor);
		}
	}

	public void postPass() {
		for (ASTNode node : unitsToProcess) {
			postPass(node);
		}
	}

	public void postPass(ASTNode node) {
		PPADefaultBindingResolver resolver = null;
		Object tempResolver = node.ast.getBindingResolver();
		if (tempResolver instanceof PPADefaultBindingResolver) {
			resolver = (PPADefaultBindingResolver) tempResolver;
		} else if (tempResolver instanceof DefaultBindingResolver) {
			resolver = new PPADefaultBindingResolver(
					(DefaultBindingResolver) node.ast.getBindingResolver(), typeRegistry);
			node.ast.setBindingResolver(resolver);
			logger.warn("Resolver is not PPA in post process");
		} else {
			return;
		}

		org.eclipse.jdt.internal.compiler.ast.ASTNode internalNode = (org.eclipse.jdt.internal.compiler.ast.ASTNode) resolver.newAstToOldAst
				.get(node);
		if (internalNode instanceof org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration) {
			org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration cud = (org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration) internalNode;
			boolean oldValue = cud.ignoreFurtherInvestigation;
			cud.ignoreFurtherInvestigation = false;
			PPAInternalPostProcessor processor = new PPAInternalPostProcessor(resolver, typeRegistry);
			cud.traverse(processor, cud.scope);
			cud.ignoreFurtherInvestigation = oldValue;
		}

	}

	public void setAllowCollectiveMode(boolean allowCollectiveMode) {
		this.allowCollectiveMode = allowCollectiveMode;
	}

	public void setAllowMemberInference(boolean allowMemberInference) {
		this.allowMemberInference = allowMemberInference;
	}

	public void setAllowMethodBindingMode(boolean allowMethodBindingMode) {
		this.allowMethodBindingMode = allowMethodBindingMode;
	}

	public void setAllowTypeInferenceMode(boolean allowTypeInferenceMode) {
		this.allowTypeInferenceMode = allowTypeInferenceMode;
	}

	public void setMaxMISize(int maxMISize) {
		this.maxMISize = maxMISize;
	}

	public void typeInferencePass() {
		processWorklist();
	}

}
