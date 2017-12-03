package io.gitlab.arturbosch.detekt.generator.collection

import io.gitlab.arturbosch.detekt.api.DetektVisitor
import io.gitlab.arturbosch.detekt.rules.isOverridden
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSuperTypeList
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression

/**
 * @author Marvin Ramin
 */
data class RuleSetProvider(
		val name: String,
		val description: String,
		val active: Boolean,
		val rules: List<String> = listOf()
)

class RuleSetProviderCollector : Collector<RuleSetProvider> {
	override val items = mutableListOf<RuleSetProvider>()

	override fun visit(file: KtFile) {
		val visitor = RuleSetProviderVisitor()
		file.accept(visitor)

		if (visitor.containsRuleSetProvider) {
			items.add(visitor.getRuleSetProvider())
		}
	}
}

private const val TAG_ACTIVE = "active"
private const val PROPERTY_RULE_SET_ID = "ruleSetId"

class RuleSetProviderVisitor : DetektVisitor() {
	var containsRuleSetProvider = false
	private var name: String = ""
	private var description: String = ""
	private var active: Boolean = false
	private val ruleNames: MutableList<String> = mutableListOf()

	fun getRuleSetProvider(): RuleSetProvider {
		if (description.isEmpty()) {
			println("Missing description for RuleSet $name")
		}

		return RuleSetProvider(name, description, active, ruleNames)
	}

	override fun visitSuperTypeList(list: KtSuperTypeList) {
		containsRuleSetProvider = list.entries
				?.map { it.typeAsUserType?.referencedName }
				?.contains(RuleSetProvider::class.simpleName) ?: false
		super.visitSuperTypeList(list)
	}

	override fun visitClassOrObject(classOrObject: KtClassOrObject) {
		description = classOrObject.docComment?.getDefaultSection()?.getContent()?.trim() ?: ""
		active = classOrObject.docComment?.getDefaultSection()?.findTagByName(TAG_ACTIVE) != null
		super.visitClassOrObject(classOrObject)
	}

	override fun visitProperty(property: KtProperty) {
		super.visitProperty(property)
		if (property.isOverridden() && property.name != null && property.name == PROPERTY_RULE_SET_ID) {
			name = (property.initializer as? KtStringTemplateExpression)?.entries?.get(0)?.text
					?: throw InvalidRuleSetProviderException("RuleSetProvider class " +
					"${property.containingClass()?.name ?: ""} doesn't provide list of rules.")
		}
	}

	override fun visitCallExpression(expression: KtCallExpression) {
		super.visitCallExpression(expression)

		if (expression.calleeExpression?.text == "RuleSet") {
			val ruleListExpression = expression.valueArguments
					.map { it.getArgumentExpression() }
					.firstOrNull { it?.referenceExpression()?.text == "listOf" }
					?: throw InvalidRuleSetProviderException("RuleSetProvider $name doesn't provide list of rules.")

			val ruleArgumentNames = (ruleListExpression as? KtCallExpression)
					?.valueArguments
					?.map { it.getArgumentExpression() }
					?.mapNotNull { it?.referenceExpression()?.text }
					?: emptyList()

			ruleNames.addAll(ruleArgumentNames)
		}
	}
}

class InvalidRuleSetProviderException(message: String) : Exception(message)