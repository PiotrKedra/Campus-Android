package de.tum.`in`.tumcampusapp.component.tumui.roomfinder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.squareup.picasso.Picasso
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding
import de.tum.`in`.tumcampusapp.R
import de.tum.`in`.tumcampusapp.api.navigatum.domain.NavigationDetails
import de.tum.`in`.tumcampusapp.api.navigatum.domain.NavigationMap
import de.tum.`in`.tumcampusapp.component.other.generic.fragment.BaseFragment
import de.tum.`in`.tumcampusapp.databinding.FragmentNavigationDetailsBinding
import de.tum.`in`.tumcampusapp.databinding.NavigationPropertyRowBinding
import de.tum.`in`.tumcampusapp.databinding.ToolbarNavigationBinding
import de.tum.`in`.tumcampusapp.di.ViewModelFactory
import de.tum.`in`.tumcampusapp.di.injector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.anko.sdk27.coroutines.onItemSelectedListener
import javax.inject.Inject
import javax.inject.Provider

class NavigationDetailsFragment : BaseFragment<Unit>(
    layoutId = R.layout.fragment_navigation_details,
    titleResId = R.string.roomfinder
) {

    private val navigationEntityId: String? by lazy {
        arguments?.getSerializable(NAVIGATION_ENTITY_ID) as String
    }

    @Inject
    lateinit var viewModelProvider: Provider<NavigationDetailsViewModel>

    private val binding by viewBinding(FragmentNavigationDetailsBinding::bind)
    private val bindingToolbar by viewBinding(ToolbarNavigationBinding::bind)

    private val viewModel: NavigationDetailsViewModel by lazy {
        val factory = ViewModelFactory(viewModelProvider)
        ViewModelProvider(this, factory).get(NavigationDetailsViewModel::class.java)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        injector.navigationDetailsComponent().inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindingToolbar.toolbar.setTitle(R.string.location_details)

        lifecycleScope.launch {
            handleDetailsLoading()
        }

        navigationEntityId?.let {
            viewModel.loadNavigationDetails(navigationEntityId!!)
        } ?: run {
            showLoadingError()
        }
    }

    private suspend fun handleDetailsLoading() {
        viewModel.state.collect { state ->

            if (state.isLoading)
                binding.progressIndicator.show()
            else
                binding.progressIndicator.hide()

            if (state.navigationDetails != null) {
                showLocationDetails(state.navigationDetails)
            }
        }
    }

    private fun showLocationDetails(navigationDetails: NavigationDetails) {
        binding.parentLocations.text = navigationDetails.getFormattedParentNames()
        binding.locationName.text = navigationDetails.name
        binding.locationType.text = getCapitalizeType(navigationDetails.type)

        setOpenInOtherAppBtnListener(navigationDetails)

        showNavigationDetailsProperties(navigationDetails)

        showAvailableMaps(navigationDetails)
    }

    private fun setOpenInOtherAppBtnListener(navigationDetails: NavigationDetails) {
        binding.openLocationBtn.setOnClickListener {
            // having to specify the location is a google maps workaround... sigh..
            // As of 25.7.22 this is absolutely needed to make Google Maps display a pin with custom name
            val coordinates = "${navigationDetails.cordsLat},${navigationDetails.cordsLon}?q=${navigationDetails.cordsLat},${navigationDetails.cordsLon}(${navigationDetails.name})"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coordinates"))
            startActivity(intent)
        }
    }

    private fun showNavigationDetailsProperties(navigationDetails: NavigationDetails) {
        navigationDetails.properties.forEach { property ->
            val propertyRow = NavigationPropertyRowBinding.inflate(layoutInflater, binding.propsList, true)
            propertyRow.propertyName.text = getTranslationForPropertyTitle(property.title)
            propertyRow.propertyValue.text = property.value
        }
    }

    private fun showAvailableMaps(navigationDetails: NavigationDetails) {
        val availableMaps = navigationDetails.availableMaps
        if (availableMaps.isNotEmpty()) {
            val map = availableMaps[0]
            loadMapImage(map)

            val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.availableMapSpinner.adapter = adapter
            availableMaps.forEach {
                adapter.add(it.mapName)
            }
            binding.availableMapSpinner.onItemSelectedListener {
                this.onItemSelected { adapterView, _, position, _ ->
                    val selectedMapName = adapterView?.getItemAtPosition(position).toString()
                    val selectedMap = availableMaps.find { it.mapName == selectedMapName }
                    selectedMap?.let {
                        loadMapImage(it)
                    }
                }
            }
            adapter.notifyDataSetChanged()
        } else {
            Picasso.get()
                .load(R.drawable.site_plans_not_available)
                .into(binding.photoView)
        }

        binding.interactiveMapBtn.setOnClickListener {
            showInteractiveMapDialog()
        }
    }

    private fun loadMapImage(map: NavigationMap) {
        val pinDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_place, null)
            ?: throw IllegalStateException("Pin icon not loaded")
        val pointerDrawer = DrawCordsPointerTransformation(
            cordX = map.pointerXCord,
            cordY = map.pointerYCord,
            pinDrawable = pinDrawable
        )
        Picasso.get()
            .load(map.getFullMapImgUrl())
            .transform(pointerDrawer)
            .into(binding.photoView)
    }

    private fun getTranslationForPropertyTitle(title: String): String {
        return when (title) {
            "Raumkennung" -> getString(R.string.room_id)
            "Adresse" -> getString(R.string.address)
            "Architekten-Name" -> getString(R.string.architect_name)
            "Sitzplätze" -> getString(R.string.seats)
            "Anzahl Räume" -> getString(R.string.number_of_rooms)
            else -> title
        }
    }

    private fun getCapitalizeType(type: String): String {
        return type.replaceFirstChar { it.titlecase() }
    }

    private fun showInteractiveMapDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.not_implemented)
            .setMessage(R.string.not_implemented_interactive_map)
            .setPositiveButton(R.string.redirect) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://nav.tum.sexy/room/${viewModel.state.value.navigationDetails?.id}"))
                startActivity(intent)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_corners_background)
        dialog.show()
        dialog.changeThemeColor()
    }

    private fun showLoadingError() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.error_something_wrong)
            .create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_corners_background)
        dialog.show()
    }

    companion object {
        const val NAVIGATION_ENTITY_ID = "navigationEntityID"

        fun newInstance(navigationEntityID: String) = NavigationDetailsFragment().apply {
            arguments = bundleOf(NAVIGATION_ENTITY_ID to navigationEntityID)
        }
    }
}

fun AlertDialog.changeThemeColor() {
    this.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(context, R.color.text_primary))
    this.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
}
