package com.greentopli.app.user.ui.purchase;


import android.app.SearchManager;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;
import com.greentopli.CommonUtils;
import com.greentopli.Constants;
import com.greentopli.app.R;
import com.greentopli.app.user.tool.ListItemDecoration;
import com.greentopli.app.user.adapter.ProductAdapter;
import com.greentopli.core.presenter.browse.BrowseProductsPresenter;
import com.greentopli.core.presenter.browse.BrowseProductsView;
import com.greentopli.core.service.ProductService;
import com.greentopli.core.storage.helper.ProductDbHelper;
import com.greentopli.core.storage.product.ProductColumns;
import com.greentopli.core.storage.product.ProductCursor;
import com.greentopli.model.Product;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindString;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;


public class BrowseProductsFragment extends Fragment implements BrowseProductsView, SearchView.OnQueryTextListener,
SearchView.OnCloseListener,SwipeRefreshLayout.OnRefreshListener, LoaderManager.LoaderCallbacks<Cursor>{

	@BindView(R.id.browse_products_recyclerView) RecyclerView mRecyclerView;
	@BindView(R.id.default_progressbar) ProgressBar mProgressBar;
	@BindView(R.id.toolbar_browseProduct_fragment)Toolbar mToolbar;
	@BindView(R.id.spinner_browse_fragment) Spinner mSpinnerVegetableType;
	@BindView(R.id.browse_products_swipeRefreshLayout) SwipeRefreshLayout mSwipeRefreshLayout;
	@BindView(R.id.browse_products_empty_message_textView) TextView mEmptyMessage;

	@BindString(R.string.app_name) String mAppName;
	private ProductAdapter mAdapter;
	private BrowseProductsPresenter mPresenter;
	private RecyclerView.LayoutManager mLayoutManager;
	private FirebaseAnalytics mAnalytics;
	private int mRestoredScrollPosition = 0;
	private int mRestoredCategoryPosition = 0;
	private String mRestoredSearchQuery = "";

	private static final String KEY_SCROLL_POSITION ="scroll_position";
	private static final String KEY_SEARCH_QUERY="search_query";
	private static final String KEY_CATEGORY_POSITION="category_position";


	SearchView mSearchView;
	OnFragmentInteractionListener listener;

	public BrowseProductsFragment() {
		// Required empty public constructor
	}
	public static BrowseProductsFragment getInstance(){
		BrowseProductsFragment fragment = new BrowseProductsFragment();
		// set parameters
		return fragment;
	}
	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		if (context instanceof OnFragmentInteractionListener){
			listener = (OnFragmentInteractionListener)context;
		}else {
			throw new RuntimeException(context.toString()+" Must implement "+
			OnFragmentInteractionListener.class.getSimpleName());
		}
	}
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		return new CursorLoader(getContext(), ProductColumns.CONTENT_URI,null,null,null,null);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		ProductDbHelper dbHelper = new ProductDbHelper(getContext());
		ProductCursor cursor = new ProductCursor(data);
		List<Product> products = new ArrayList<>();
		while (cursor.moveToNext()){
			products.add(dbHelper.getProductFromCursor(cursor));
		}
		cursor.close();
		if (products.size()>0){
			showProducts(products);
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle bundle) {
		// Inflate the layout for this fragment
		View rootView =  inflater.inflate(R.layout.fragment_browse_product, container, false);
		ButterKnife.bind(this,rootView);
		//set toolbar as Actionbar
		((AppCompatActivity)getActivity()).setSupportActionBar(mToolbar);
		ActionBar actionBar = ((AppCompatActivity)getActivity()).getSupportActionBar();
		if (actionBar!=null){
			actionBar.setTitle(mAppName);
		}
		// enable menu options
		setHasOptionsMenu(true);
		if (bundle!=null){
			if (bundle.containsKey(KEY_SCROLL_POSITION))
				mRestoredScrollPosition = bundle.getInt(KEY_SCROLL_POSITION);

			if (bundle.containsKey(KEY_SEARCH_QUERY))
				mRestoredSearchQuery = bundle.getString(KEY_SEARCH_QUERY);
			else if (bundle.containsKey(KEY_CATEGORY_POSITION))
				mRestoredCategoryPosition = bundle.getInt(KEY_CATEGORY_POSITION);

		}
		mPresenter = BrowseProductsPresenter.bind(this,getContext());
		mAnalytics = FirebaseAnalytics.getInstance(getContext());
		mSwipeRefreshLayout.setOnRefreshListener(this);
		mRecyclerView.addItemDecoration(new ListItemDecoration(getContext()));
		return rootView;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {

		if (mLayoutManager!=null){
			mRestoredScrollPosition = ((LinearLayoutManager) mLayoutManager).findFirstCompletelyVisibleItemPosition();
			if (mRestoredScrollPosition >0)
				outState.putInt(KEY_SCROLL_POSITION, mRestoredScrollPosition);
		}

		if (mSearchView!=null && !mSearchView.getQuery().toString().isEmpty())
			outState.putString(KEY_SEARCH_QUERY,mSearchView.getQuery().toString());
		else if (mSpinnerVegetableType!=null && mSpinnerVegetableType.getSelectedItemPosition()>0)
			outState.putInt(KEY_CATEGORY_POSITION,mSpinnerVegetableType.getSelectedItemPosition());

		super.onSaveInstanceState(outState);
	}

	private void initRecyclerView(){
		// prepare recycler view for incoming data
		mLayoutManager = new LinearLayoutManager(getContext());
		mRecyclerView.setLayoutManager(mLayoutManager);
		mAdapter = new ProductAdapter(ProductAdapter.Mode.BROWSE,getContext());
		mRecyclerView.setAdapter(mAdapter);
		// restore scroll position on orientation change
		if (mRestoredScrollPosition >0){
			mRecyclerView.scrollToPosition(mRestoredScrollPosition);
			mRestoredScrollPosition = 0;
		}
		showEmpty(false);
	}
	@Override
	public void onDestroy() {
		if (mPresenter!=null) // to avoid instant run errors
			mPresenter.detachView();
		super.onDestroy();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.product_option_menu,menu);
		// add search bar
		MenuItem itemSearch = menu.findItem(R.id.menu_search_browse_product);
		SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);

		if (itemSearch!=null){
			mSearchView = (SearchView) MenuItemCompat.getActionView(itemSearch);
			MenuItemCompat.collapseActionView(itemSearch);
		}
		if (mSearchView!=null){
			mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));
			mSearchView.setOnQueryTextListener(this);
			mSearchView.setOnCloseListener(this);
			// avoid full screen keyboard in landscape mode
			mSearchView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
			if (!mRestoredSearchQuery.isEmpty() && itemSearch!=null){
				// expand searchview
				mSearchView.setQuery(mRestoredSearchQuery,false);
				mSearchView.setIconified(false);
				mSearchView.clearFocus();
			}
		}
		super.onCreateOptionsMenu(menu,inflater);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),
				R.layout.spinner_row, CommonUtils.getFoodCategories());
		mSpinnerVegetableType.setAdapter(adapter);

		/**
		 * View gets updated when user selects Vegetable category from spinner.
		 * Also executed once on initialization
		 *
		 * Also it reports selected category to Analytics
		 */
		mSpinnerVegetableType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				if (position>=0 && position < CommonUtils.getFoodCategories().size()){
					String vegetableType = CommonUtils.getFoodCategories().get(position);
					// disable sort by category while searching products
					if (mSearchView.getQuery().toString().isEmpty()){
						mPresenter.sortProducts(vegetableType);
					} else {
						// when search query is present, user will not be able to select any category
						mSpinnerVegetableType.setSelection(0,true);
					}
					// report category selection to analytics
					if (!vegetableType.toLowerCase().equals(Product.Type.ALL.name().toLowerCase())){
						Bundle argument = new Bundle();
						argument.putString(FirebaseAnalytics.Param.ITEM_CATEGORY,vegetableType);
						mAnalytics.logEvent(Constants.EVENT_CATEGORY_SELECTED,argument);
					}
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {}
		});

		if (mRestoredCategoryPosition > 0){
			mSpinnerVegetableType.setSelection(mRestoredCategoryPosition,true);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()){
			case R.id.menu_search_browse_product:
				return false;
			default:
				break;
		}
		mSearchView.setOnQueryTextListener(this);
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onProductServiceFinished() {
		LoaderManager manager = getActivity().getSupportLoaderManager();
		manager.restartLoader(0,null,this);
		manager.initLoader(0,null,this);
	}

	@Override
	public void showEmpty(boolean show) {
		mSwipeRefreshLayout.setRefreshing(false);
		mRecyclerView.setVisibility(show?View.GONE:View.VISIBLE);
		mEmptyMessage.setVisibility(show?View.VISIBLE:View.GONE);
	}

	@Override
	public void showError(String message) {
		Toast.makeText(getContext(),R.string.error_getting_products,Toast.LENGTH_LONG).show();
		FirebaseCrash.log(Constants.ERROR_PRODUCT_LOADING+message);
		mSwipeRefreshLayout.setRefreshing(false);
	}

	@Override
	public void showProducts(List<Product> list) {
		// Reinitialise RecyclerView so, it will avoid duplication
		initRecyclerView();
		mAdapter.addNewProducts(list);
		mSwipeRefreshLayout.setRefreshing(false);
	}

	@OnClick(R.id.fab_browse_product_fragment)
	void onCheckoutBegin(){
		if (mAdapter.getCartItemCount()>0){
			// avoiding state persistence of list
			mRestoredCategoryPosition = 0;
			mRestoredScrollPosition = 0;
			mRestoredSearchQuery = "";
			listener.onFragmentInteraction();
		}else {
			Toast.makeText(getContext(),R.string.message_empty_cart,Toast.LENGTH_SHORT).show();
		}
	}
	@Override
	public void showProgressbar(boolean show) {
		showEmpty(false);
		mSwipeRefreshLayout.setRefreshing(false);
		mProgressBar.setVisibility(show?View.VISIBLE:View.GONE);
	}

	// On Swipe Refresh layout
	@Override
	public void onRefresh() {
		ProductService.start(getContext());
	}

	// Search Query Handler
	@Override
	public boolean onQueryTextSubmit(String query) {
		return true;
	}

	@Override
	public boolean onQueryTextChange(String newText) {
		mPresenter.searchProduct(newText);
		mSpinnerVegetableType.setSelection(0,true);
		return true;
	}

	// On closing SearchBar
	@Override
	public boolean onClose() {
		mRestoredSearchQuery = "";
		mSpinnerVegetableType.setSelection(0);
		return false;
	}
}
