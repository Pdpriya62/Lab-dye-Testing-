"""
Analysis for the research paper: which conditions explain colour deviation (Delta-E), and can we predict it?

Works with TWO input formats (auto-detected):
  A) sample_results.csv   (from generate_labdip_data.py: one row per sample)
  B) trials_real.csv      (from logger.py: one row per reading; aggregated to one row per sample here)

  pip install pandas numpy scipy statsmodels scikit-learn matplotlib
  python analyze.py sample_results.csv
  python analyze.py trials_real.csv --setpoint 60 --lux-target 1200
Outputs go to ./results (plots + metrics CSV).
"""
import argparse, os
import numpy as np, pandas as pd, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import statsmodels.api as sm, statsmodels.formula.api as smf
from sklearn.model_selection import KFold, cross_val_predict
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import LinearRegression
from sklearn.ensemble import RandomForestRegressor, RandomForestClassifier
from sklearn.metrics import r2_score, mean_squared_error, mean_absolute_error, accuracy_score
from sklearn.inspection import permutation_importance

ap = argparse.ArgumentParser()
ap.add_argument("path", nargs="?", default="sample_results.csv")
ap.add_argument("--setpoint", type=float, default=60.0, help="recipe bath temp (C) if the CSV has no setpoint_c column")
ap.add_argument("--lux-target", type=float, default=1200.0, help="target light-box illuminance (lux)")
ap.add_argument("--pass-limit", type=float, default=1.0, help="Delta-E pass/fail limit")
a = ap.parse_args()
os.makedirs("results", exist_ok=True)
raw = pd.read_csv(a.path)

# ---------------------------------------------------------------- 1) Build ONE row per sample
if "delta_E_2000" in raw.columns:                       # format A
    print("Detected format A (sample_results.csv)")
    s = pd.DataFrame({
        "sample_id": raw["sample_id"],
        "deltaE": raw["delta_E_2000"],
        "setpoint": raw["setpoint_c"],
        "bath_mean": raw["hold_mean_temp_c"],
        "bath_std": raw["hold_temp_std_c"],
        "ambient_temp": raw["ambient_temp_at_measure_c"],
        "humidity": raw["humidity_at_measure_pct"],
        "lux": raw["light_lux_at_measure"],
        "dye_conc": raw["dye_conc_pct_owf"],
        "hold_time": raw["hold_time_min"],
        "fabric": raw["fabric"],
    })
    if "dye_class" in raw: s["dye_class"] = raw["dye_class"]
else:                                                    # format B (logger.py output)
    print("Detected format B (logger.py readings) - aggregating per sample")
    need = ["sample_id", "deltaE", "bath_temp", "humidity", "light_lux", "ambient_temp"]
    raw = raw.dropna(subset=need)
    g = raw.groupby("sample_id")
    s = pd.DataFrame({
        "deltaE": g["deltaE"].mean(),
        "bath_mean": g["bath_temp"].mean(),
        "bath_std": g["bath_temp"].std().fillna(0),
        "ambient_temp": g["ambient_temp"].mean(),
        "humidity": g["humidity"].mean(),
        "lux": g["light_lux"].mean(),
    }).reset_index()
    s["setpoint"] = g["setpoint_c"].first().values if "setpoint_c" in raw else a.setpoint
    for c in ["dye_conc", "fabric", "dye"]:
        if c in raw: s[c] = g[c].first().values

s = s.dropna(subset=["deltaE", "bath_mean", "humidity", "lux", "ambient_temp"]).reset_index(drop=True)

# ---------------------------------------------------------------- 2) Engineered features (relative to the RECIPE, not a fixed 60 C)
s["temp_dev"] = s["bath_mean"] - s["setpoint"]                 # signed: negative = bath ran cold
s["temp_under"] = (s["setpoint"] - s["bath_mean"]).clip(lower=0)   # shortfall only (cold bath -> lighter shade)
s["lux_dev_pct"] = (s["lux"] - a.lux_target) / a.lux_target * 100  # signed % deviation from target lighting
s["result"] = np.where(s["deltaE"] <= a.pass_limit, "PASS", "FAIL")

num = ["temp_dev", "temp_under", "bath_std", "humidity", "ambient_temp", "lux_dev_pct"]
for c in ["dye_conc", "hold_time"]:
    if c in s: num.append(c)

# Fabric as categories (one-hot). dye_class is NOT added: it is fully determined by fabric/setpoint
# (collinear), so adding it would only break the regression.
cat_cols = [c for c in ["fabric"] if c in s]
X = pd.concat([s[num], pd.get_dummies(s[cat_cols], drop_first=True).astype(float)], axis=1) if cat_cols else s[num].copy()
feat = list(X.columns)
y = s["deltaE"].values

print(f"\nSamples: {len(s)}   Pass: {(s.result=='PASS').sum()}   Fail: {(s.result=='FAIL').sum()}   (limit dE <= {a.pass_limit})")
print(s[["deltaE"] + num].describe().round(2).T, "\n")
if cat_cols:
    print("Mean Delta-E and pass rate by fabric:")
    print(s.groupby("fabric").agg(n=("deltaE", "size"), mean_dE=("deltaE", "mean"),
                                  pass_rate=("result", lambda r: (r == "PASS").mean())).round(2), "\n")

# ---------------------------------------------------------------- 3) Correlation
corr = pd.concat([X, s["deltaE"]], axis=1).corr(method="spearman")
print("Spearman correlation with Delta-E:\n", corr["deltaE"].drop("deltaE").sort_values(key=abs, ascending=False).round(3), "\n")
plt.figure(figsize=(7, 6)); plt.imshow(corr, cmap="coolwarm", vmin=-1, vmax=1)
plt.xticks(range(len(corr)), corr.columns, rotation=45, ha="right"); plt.yticks(range(len(corr)), corr.columns)
plt.colorbar(); plt.title("Spearman correlation"); plt.tight_layout(); plt.savefig("results/correlation.png", dpi=150); plt.close()

# ---------------------------------------------------------------- 4) Scatter plots
show = ["temp_dev", "humidity", "lux_dev_pct"]
fig, ax = plt.subplots(1, 3, figsize=(13, 3.8))
for ax_, c in zip(ax, show):
    ax_.scatter(s[c], s["deltaE"], s=16, alpha=.6); ax_.set_xlabel(c); ax_.set_ylabel("Delta-E")
    ax_.axhline(a.pass_limit, color="r", ls="--", lw=1)
plt.tight_layout(); plt.savefig("results/scatter.png", dpi=150); plt.close()

# ---------------------------------------------------------------- 5) OLS + ANOVA (statistical significance)
terms = " + ".join(num + [f"C({c})" for c in cat_cols])
ols = smf.ols(f"deltaE ~ {terms}", data=s).fit()
print(ols.summary().tables[1]); print(f"\nOLS R2 = {ols.rsquared:.3f}   adj R2 = {ols.rsquared_adj:.3f}\n")
print(sm.stats.anova_lm(ols, typ=2).round(4), "\n")

# ---------------------------------------------------------------- 6) Prediction models (5-fold CV; one row = one sample, so plain KFold is correct)
cv = KFold(n_splits=min(5, len(s)), shuffle=True, random_state=0)
models = {"Linear Regression": make_pipeline(StandardScaler(), LinearRegression()),
          "Random Forest": RandomForestRegressor(n_estimators=300, random_state=0)}
res = []
for name, m in models.items():
    p = cross_val_predict(m, X.values, y, cv=cv)
    res.append(dict(model=name, R2=r2_score(y, p), RMSE=np.sqrt(mean_squared_error(y, p)), MAE=mean_absolute_error(y, p)))
    if name == "Random Forest":
        plt.figure(figsize=(4.5, 4.5)); plt.scatter(y, p, s=16, alpha=.6)
        lim = [min(y.min(), p.min()), max(y.max(), p.max())]; plt.plot(lim, lim, "r--")
        plt.xlabel("Measured Delta-E"); plt.ylabel("Predicted Delta-E"); plt.title("Random Forest (5-fold CV)")
        plt.tight_layout(); plt.savefig("results/pred_vs_actual.png", dpi=150); plt.close()
res = pd.DataFrame(res).round(3); print(res.to_string(index=False)); res.to_csv("results/model_metrics.csv", index=False)

# Pass/Fail classifier (what a lab actually wants: will this dip be approved?)
if s["result"].nunique() == 2:
    yc = (s["result"] == "PASS").astype(int).values
    pc = cross_val_predict(RandomForestClassifier(n_estimators=300, random_state=0), X.values, yc, cv=cv)
    print(f"\nPASS/FAIL classifier accuracy (CV): {accuracy_score(yc, pc):.3f}   (baseline = always '{'PASS' if yc.mean()>=.5 else 'FAIL'}': {max(yc.mean(), 1-yc.mean()):.3f})")

# ---------------------------------------------------------------- 7) Which condition drives Delta-E?
rf = models["Random Forest"].fit(X.values, y)
pi = permutation_importance(rf, X.values, y, n_repeats=20, random_state=0)
imp = pd.Series(pi.importances_mean, index=feat).sort_values()
print("\nPermutation importance (higher = matters more):\n", imp.round(4)[::-1])
plt.figure(figsize=(6.5, 4.5)); imp.plot.barh(); plt.title("Which condition drives Delta-E?"); plt.tight_layout()
plt.savefig("results/feature_importance.png", dpi=150); plt.close()
print("\nPlots + metrics saved to ./results")
