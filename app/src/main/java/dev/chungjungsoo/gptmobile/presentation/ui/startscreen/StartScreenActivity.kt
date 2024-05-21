package dev.chungjungsoo.gptmobile.presentation.ui.startscreen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupActivity

class StartScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GPTMobileTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        StartScreenLogo()
                        Spacer(modifier = Modifier.weight(1f))
                        WelcomeText()
                        PrimaryLongButton(
                            onClick = {
                                val intent = Intent(this@StartScreenActivity, SetupActivity::class.java)
                                startActivity(intent)
                            },
                            text = stringResource(R.string.get_started)
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun StartScreenLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.gpt_mobile_start_screen),
        contentDescription = stringResource(R.string.gpt_mobile_introduction_logo),
        contentScale = ContentScale.FillHeight,
        modifier = modifier
            .padding(top = 50.dp)
            .height(400.dp)
    )
}

@Preview
@Composable
fun WelcomeText(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            modifier = Modifier
                .padding(4.dp)
                .semantics { heading() },
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            modifier = Modifier.padding(4.dp),
            text = stringResource(R.string.welcome_description),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
