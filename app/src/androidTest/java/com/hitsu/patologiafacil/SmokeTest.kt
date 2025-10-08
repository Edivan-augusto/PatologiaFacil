package com.hitsu.patologiafacil

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule
    val rule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun hasEnterButton_and_navigates() {
        onView(withText("Entrar")).check(matches(isDisplayed()))
        onView(withText("Entrar")).perform(click())
        onView(withText("Como funciona")).check(matches(isDisplayed()))
    }
}
